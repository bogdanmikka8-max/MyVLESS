package com.myvless.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            boolean startOnBoot = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("start_on_boot", false);
            if (startOnBoot) {
                Intent vpnIntent = new Intent(context, MyVpnService.class);
                vpnIntent.setAction(MyVpnService.ACTION_CONNECT);
                context.startForegroundService(vpnIntent);
            }
        }
    }
}