package com.ftiktokmanager.app;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import org.json.JSONObject;

/** IP lookup, optionally through a SOCKS5 proxy (via the local SocksBridge). */
final class IpTool {
    private IpTool() {}

    static final class Result {
        String ip = "", country = "", cc = "", region = "", city = "", isp = "", tz = "", type = "", asn = "";
    }

    /** host empty = direct connection. */
    static Result fetch(String host, int port, String user, String pass) throws IOException {
        SocksBridge bridge = null;
        Proxy proxy = null;
        try {
            if (host != null && !host.isEmpty()) {
                if (port <= 0) port = 8080;
                // quick check first so errors are readable (wrong host / port / password)
                SocksBridge.openUpstream(host, port, user, pass, "ipwho.is", 443).close();
                bridge = new SocksBridge(host, port, user, pass);
                int bp = bridge.start();
                proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", bp));
            }
            try {
                JSONObject j = new JSONObject(httpGet("https://ipwho.is/", proxy));
                if (!j.optBoolean("success", true)) throw new IOException("lookup failed");
                Result r = new Result();
                r.ip = j.optString("ip", "");
                r.country = j.optString("country", "");
                r.cc = j.optString("country_code", "");
                r.region = j.optString("region", "");
                r.city = j.optString("city", "");
                r.type = j.optString("type", "");
                JSONObject conn = j.optJSONObject("connection");
                if (conn != null) {
                    r.isp = conn.optString("isp", conn.optString("org", ""));
                    int a = conn.optInt("asn", 0);
                    r.asn = a == 0 ? "" : "AS" + a;
                }
                JSONObject tz = j.optJSONObject("timezone");
                r.tz = tz == null ? "" : tz.optString("id", "");
                return r;
            } catch (Exception first) {
                try {
                    JSONObject j = new JSONObject(httpGet("https://ipapi.co/json/", proxy));
                    Result r = new Result();
                    r.ip = j.optString("ip", "");
                    r.country = j.optString("country_name", "");
                    r.cc = j.optString("country_code", "");
                    r.region = j.optString("region", "");
                    r.city = j.optString("city", "");
                    r.isp = j.optString("org", "");
                    r.asn = j.optString("asn", "");
                    r.tz = j.optString("timezone", "");
                    if (r.ip.isEmpty()) throw new IOException("empty");
                    return r;
                } catch (Exception second) {
                    throw new IOException("Checker service ne jawab nahi diya (" + String.valueOf(second.getMessage()) + ")");
                }
            }
        } finally {
            if (bridge != null) bridge.stop();
        }
    }

    static String test(String host, int port, String user, String pass) throws IOException {
        Result r = fetch(host, port, user, pass);
        return "✅ Connected!\nExit IP: " + r.ip + "\n" + r.country + " - " + r.city;
    }

    private static String httpGet(String u, Proxy proxy) throws IOException {
        URL url = new URL(u);
        HttpURLConnection c = (HttpURLConnection) (proxy == null ? url.openConnection() : url.openConnection(proxy));
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "TikTokManager/1.2");
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            return BackupManager.readAll(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }
}
