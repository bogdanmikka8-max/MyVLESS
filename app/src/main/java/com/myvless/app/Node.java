package com.myvless.app;

import org.json.JSONObject;
import org.json.JSONException;

public class Node {
    public String uri;
    public String server;
    public int port;
    public String uuid;
    public String remark;
    public int ping = 9999;
    public long pingTime = 0;
    public int failCount = 0;

    public Node(String uri) {
        this.uri = uri;
        parseURI(uri);
    }

    private void parseURI(String uri) {
        if (uri == null) return;
        String u = uri.trim();
        if (u.startsWith("vless://")) {
            String body = u.substring(8);
            int atIndex = body.indexOf('@');
            if (atIndex > 0) {
                this.uuid = body.substring(0, atIndex);
                String rest = body.substring(atIndex + 1);
                int colon = rest.indexOf(':');
                if (colon > 0) {
                    this.server = rest.substring(0, colon);
                    String afterColon = rest.substring(colon + 1);
                    int hashQ = afterColon.indexOf('#');
                    if (hashQ > 0) {
                        try { this.port = Integer.parseInt(afterColon.substring(0, hashQ)); } catch (NumberFormatException ignored) {}
                        this.remark = afterColon.substring(hashQ + 1);
                    } else {
                        int qm = afterColon.indexOf('?');
                        if (qm > 0) {
                            try { this.port = Integer.parseInt(afterColon.substring(0, qm)); } catch (NumberFormatException ignored) {}
                        } else {
                            try { this.port = Integer.parseInt(afterColon); } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        }
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("uri", uri);
            obj.put("server", server);
            obj.put("port", port);
            obj.put("uuid", uuid);
            obj.put("remark", remark != null ? remark : "");
            obj.put("ping", ping);
            obj.put("pingTime", pingTime);
            obj.put("failCount", failCount);
        } catch (JSONException ignored) {}
        return obj;
    }

    public static Node fromJson(JSONObject obj) {
        Node n = new Node(obj.optString("uri", ""));
        n.server = obj.optString("server", "");
        n.port = obj.optInt("port", 0);
        n.uuid = obj.optString("uuid", "");
        n.remark = obj.optString("remark", "");
        n.ping = obj.optInt("ping", 9999);
        n.pingTime = obj.optLong("pingTime", 0);
        n.failCount = obj.optInt("failCount", 0);
        return n;
    }
}
