package com.myvless.app;

import android.content.Context;
import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class SubscriptionManager {
    private static final String TAG = "SubManager";
    private static final String NODES_FILE = "nodes.json";

    private final Context context;

    public SubscriptionManager(Context context) {
        this.context = context;
    }

    public List<Node> loadNodes() {
        List<Node> nodes = new ArrayList<>();
        File file = new File(context.getFilesDir(), NODES_FILE);
        if (!file.exists()) return nodes;
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            org.json.JSONArray arr = new org.json.JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                nodes.add(Node.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Load nodes failed", e);
        }
        return nodes;
    }

    public void saveNodes(List<Node> nodes) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (Node n : nodes) arr.put(n.toJson());
            FileWriter fw = new FileWriter(new File(context.getFilesDir(), NODES_FILE));
            fw.write(arr.toString(2));
            fw.close();
        } catch (Exception e) {
            Log.e(TAG, "Save nodes failed", e);
        }
    }

    public interface FetchCallback {
        void onResult(List<Node> nodes, String error);
    }

    public void fetchSubscription(String url, String mirrorUrl, FetchCallback callback) {
        new Thread(() -> {
            List<Node> nodes = fetchUrl(url);
            if (nodes.isEmpty() && mirrorUrl != null && !mirrorUrl.isEmpty()) {
                Log.i(TAG, "Primary empty, trying mirror");
                nodes = fetchUrl(mirrorUrl);
            }
            if (!nodes.isEmpty()) {
                callback.onResult(nodes, null);
            } else {
                callback.onResult(null, "Empty response from all URLs");
            }
        }).start();
    }

    private List<Node> fetchUrl(String urlStr) {
        List<Node> nodes = new ArrayList<>();
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "MyVLESS/1.0");
            int code = conn.getResponseCode();
            Log.i(TAG, "Fetch " + urlStr + " -> " + code);
            if (code != 200) return nodes;

            InputStream in = new BufferedInputStream(conn.getInputStream());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) baos.write(buf, 0, len);
            in.close();
            byte[] data = baos.toByteArray();

            String text;
            try {
                text = new String(data, StandardCharsets.UTF_8);
            } catch (Exception e) {
                text = new String(data, StandardCharsets.ISO_8859_1);
            }

            // Try decode as base64
            try {
                byte[] decoded = Base64.getDecoder().decode(text.trim());
                text = new String(decoded, StandardCharsets.UTF_8);
            } catch (Exception ignored) {}

            // Check for profile-title header
            String profileTitle = conn.getHeaderField("profile-title");
            if (profileTitle != null) {
                try {
                    byte[] tDecoded = Base64.getDecoder().decode(profileTitle.trim());
                    profileTitle = new String(tDecoded, StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
                Log.i(TAG, "Profile title: " + profileTitle);
            }

            String[] lines = text.split("\\r?\\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("vless://")) {
                    Node node = new Node(line);
                    if (node.server != null && !node.server.isEmpty()) {
                        nodes.add(node);
                    }
                }
            }
            Log.i(TAG, "Parsed " + nodes.size() + " nodes");
        } catch (Exception e) {
            Log.e(TAG, "Fetch failed: " + urlStr, e);
        }
        return nodes;
    }
}
