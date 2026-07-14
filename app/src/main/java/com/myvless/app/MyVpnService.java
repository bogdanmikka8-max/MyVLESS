package com.myvless.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.net.ProxyInfo;

import java.io.FileDescriptor;
import java.lang.reflect.Field;

public class MyVpnService extends VpnService {
    private static final String TAG = "MyVpnService";
    public static final String ACTION_CONNECT = "com.myvless.app.CONNECT";
    public static final String ACTION_DISCONNECT = "com.myvless.app.DISCONNECT";
    public static final String EXTRA_SOCKS_PORT = "socks_port";
    public static final String EXTRA_MIXED_PORT = "mixed_port";
    public static final String EXTRA_DNS = "dns_servers";

    private CoreManager coreManager;
    private ParcelFileDescriptor vpnInterface;
    private int socksPort = 10808;
    private int mixedPort = 10809;
    private String dnsServers = "";
    private static boolean running = false;

    public static boolean isRunning() { return running; }

    @Override
    public void onCreate() {
        super.onCreate();
        coreManager = new CoreManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (ACTION_DISCONNECT.equals(action)) {
            stopVpn();
            return START_NOT_STICKY;
        }
        if (ACTION_CONNECT.equals(action)) {
            socksPort = intent.getIntExtra(EXTRA_SOCKS_PORT, 10808);
            mixedPort = intent.getIntExtra(EXTRA_MIXED_PORT, 10809);
            dnsServers = intent.getStringExtra(EXTRA_DNS);
            if (dnsServers == null) dnsServers = "";
            startVpn();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    private void startVpn() {
        if (vpnInterface != null) return;

        // Ensure core exists
        if (!coreManager.ensureCoreExists()) {
            Log.e(TAG, "Core download failed");
            stopSelf();
            return;
        }

        // Prepare notification
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, MyApplication.CHANNEL_ID)
            .setContentTitle("MyVLESS VPN")
            .setContentText("Starting...")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .build();
        startForeground(1, notification);

        // Build VPN interface
        Builder builder = new Builder();
        builder.setSession("MyVLESS");
        builder.setMtu(1500);
        builder.addAddress("10.0.0.2", 32);
        builder.addRoute("0.0.0.0", 0);
        builder.addDnsServer("1.1.1.1");
        builder.addDnsServer("8.8.8.8");
        builder.setBlocking(true);

        // Allow ourselves
        builder.addDisallowedApplication(getPackageName());

        vpnInterface = builder.establish();
        if (vpnInterface == null) {
            Log.e(TAG, "VPN establish failed");
            stopSelf();
            return;
        }

        int tunFd = vpnInterface.getFd();
        Log.i(TAG, "TUN fd: " + tunFd);

        // Load nodes
        SubscriptionManager subManager = new SubscriptionManager(this);
        java.util.List<Node> nodes = subManager.loadNodes();
        if (nodes.isEmpty()) {
            Log.e(TAG, "No nodes available");
            stopVpn();
            return;
        }

        // Start core
        boolean ok = coreManager.start(nodes, 0, dnsServers, socksPort, mixedPort, tunFd);
        if (!ok) {
            Log.e(TAG, "Core start failed");
            stopVpn();
            return;
        }

        running = true;
        updateNotification("Connected");
        Log.i(TAG, "VPN started");
    }

    private void stopVpn() {
        running = false;
        coreManager.stop();
        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (Exception ignored) {}
            vpnInterface = null;
        }
        stopForeground(true);
        stopSelf();
        Log.i(TAG, "VPN stopped");
    }

    private void updateNotification(String status) {
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, MyApplication.CHANNEL_ID)
            .setContentTitle("MyVLESS VPN")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .build();
        startForeground(1, notification);
    }

    private int getFdFromParcelFileDescriptor(ParcelFileDescriptor pfd) {
        try {
            Field f = ParcelFileDescriptor.class.getDeclaredField("mFd");
            f.setAccessible(true);
            FileDescriptor fd = (FileDescriptor) f.get(pfd);
            Field fdField = FileDescriptor.class.getDeclaredField("fd");
            fdField.setAccessible(true);
            return fdField.getInt(fd);
        } catch (Exception e) {
            Log.e(TAG, "getFd failed, using getFd()", e);
            return pfd.getFd();
        }
    }
}
