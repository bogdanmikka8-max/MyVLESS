package com.myvless.app;

import android.content.Context;
import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CoreManager {
    private static final String TAG = "CoreManager";
    private static final String CORE_URL = "https://github.com/SagerNet/sing-box/releases/download/v1.8.8/sing-box-1.8.8-android-arm64.tar.gz";
    private static final String CORE_NAME = "sing-box";

    private final Context context;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Process process;
    private Thread stdoutThread;
    private Thread stderrThread;

    public CoreManager(Context context) {
        this.context = context;
    }

    public File getCoreFile() {
        return new File(context.getFilesDir(), CORE_NAME);
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean ensureCoreExists() {
        File core = getCoreFile();
        if (core.exists() && core.length() > 100000) return true;
        Log.i(TAG, "Core not found, downloading...");
        try {
            URL url = new URL(CORE_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            InputStream in = new BufferedInputStream(conn.getInputStream());
            File tmp = new File(context.getFilesDir(), CORE_NAME + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            out.close();
            in.close();
            tmp.setExecutable(true);
            tmp.renameTo(core);
            Log.i(TAG, "Core downloaded: " + core.length() + " bytes");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            return false;
        }
    }

    public String generateConfig(List<Node> nodes, int activeIdx, String dnsServers, int socksPort, int mixedPort) {
        if (nodes == null || nodes.isEmpty() || activeIdx < 0 || activeIdx >= nodes.size()) return null;
        Node node = nodes.get(activeIdx);
        String dnsList = "[\"https://dns.google/dns-query\",\"https://1.1.1.1/dns-query\"]";
        if (dnsServers != null && !dnsServers.isEmpty()) {
            String[] parts = dnsServers.split(",");
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(parts[i].trim()).append("\"");
            }
            sb.append("]");
            dnsList = sb.toString();
        }

        return "{\n" +
            "  \"log\":{\"level\":\"warn\",\"timestamp\":true},\n" +
            "  \"dns\":{\n" +
            "    \"servers\":[\n" +
            "      {\"tag\":\"remote\",\"address\":\"https://1.1.1.1/dns-query\",\"strategy\":\"prefer_ipv4\"},\n" +
            "      {\"tag\":\"local\",\"address\":\"local\",\"detour\":\"direct\"}\n" +
            "    ],\n" +
            "    \"rules\":[{\"rule_set\":[\"geosite-cn\"],\"server\":\"local\"}]\n" +
            "  },\n" +
            "  \"inbounds\":[\n" +
            "    {\n" +
            "      \"type\":\"tun\",\n" +
            "      \"tag\":\"tun-in\",\n" +
            "      \"interface_name\":\"tun0\",\n" +
            "      \"mtu\":1500,\n" +
            "      \"auto_route\":true,\n" +
            "      \"strict_route\":true,\n" +
            "      \"sniff\":true\n" +
            "    },\n" +
            "    {\n" +
            "      \"type\":\"socks\",\n" +
            "      \"tag\":\"socks-in\",\n" +
            "      \"listen\":\"127.0.0.1\",\n" +
            "      \"listen_port\":" + socksPort + "\n" +
            "    },\n" +
            "    {\n" +
            "      \"type\":\"mixed\",\n" +
            "      \"tag\":\"mixed-in\",\n" +
            "      \"listen\":\"127.0.0.1\",\n" +
            "      \"listen_port\":" + mixedPort + "\n" +
            "    }\n" +
            "  ],\n" +
            "  \"outbounds\":[\n" +
            "    {\n" +
            "      \"type\":\"vless\",\n" +
            "      \"tag\":\"proxy\",\n" +
            "      \"server\":\"" + node.server + "\",\n" +
            "      \"server_port\":" + node.port + ",\n" +
            "      \"uuid\":\"" + node.uuid + "\",\n" +
            "      \"flow\":\"xtls-rprx-vision\"\n" +
            "    },\n" +
            "    {\"type\":\"direct\",\"tag\":\"direct\"},\n" +
            "    {\"type\":\"block\",\"tag\":\"block\"}\n" +
            "  ],\n" +
            "  \"route\":{\n" +
            "    \"auto_detect_interface\":true,\n" +
            "    \"rules\":[\n" +
            "      {\"rule_set\":[\"geosite-cn\"],\"outbound\":\"direct\"},\n" +
            "      {\"ip_is_private\":true,\"outbound\":\"direct\"}\n" +
            "    ],\n" +
            "    \"rule_set\":[\n" +
            "      {\"tag\":\"geosite-cn\",\"type\":\"remote\",\"format\":\"binary\",\"url\":\"https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-cn.srs\",\"download_detour\":\"direct\"}\n" +
            "    ],\n" +
            "    \"final\":\"proxy\"\n" +
            "  }\n" +
            "}";
    }

    public synchronized boolean start(List<Node> nodes, int activeIdx, String dnsServers, int socksPort, int mixedPort, int tunFd) {
        if (running.get()) return true;
        File core = getCoreFile();
        if (!core.exists()) {
            Log.e(TAG, "Core binary not found");
            return false;
        }
        String configJson = generateConfig(nodes, activeIdx, dnsServers, socksPort, mixedPort);
        if (configJson == null) return false;

        try {
            File configFile = new File(context.getFilesDir(), "config.json");
            FileWriter fw = new FileWriter(configFile);
            fw.write(configJson);
            fw.close();

            String[] cmd;
            if (tunFd > 0) {
                cmd = new String[]{
                    core.getAbsolutePath(), "run",
                    "-c", configFile.getAbsolutePath(),
                    "--disable-color"
                };
            } else {
                cmd = new String[]{
                    core.getAbsolutePath(), "run",
                    "-c", configFile.getAbsolutePath(),
                    "--disable-color"
                };
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(context.getFilesDir());

            if (tunFd > 0) {
                pb.redirectErrorStream(true);
                // TUN fd is passed via VpnService builder, we handle it in MyVpnService
            }

            process = pb.start();

            stdoutThread = new Thread(() -> {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        Log.d(TAG, "core: " + line);
                    }
                } catch (IOException ignored) {}
            });
            stdoutThread.setDaemon(true);
            stdoutThread.start();

            stderrThread = new Thread(() -> {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        Log.w(TAG, "core-err: " + line);
                    }
                } catch (IOException ignored) {}
            });
            stderrThread.setDaemon(true);
            stderrThread.start();

            running.set(true);
            Log.i(TAG, "Core started");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Start failed", e);
            return false;
        }
    }

    public synchronized void stop() {
        if (!running.get()) return;
        running.set(false);
        if (process != null) {
            process.destroy();
            try { process.waitFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
            process.destroyForcibly();
            process = null;
        }
        Log.i(TAG, "Core stopped");
    }
}
