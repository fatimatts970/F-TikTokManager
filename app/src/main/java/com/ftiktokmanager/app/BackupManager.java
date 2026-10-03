package com.ftiktokmanager.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Export / import of accounts (incl. saved sessions) and links as one JSON file. */
final class BackupManager {
    private static final int MAX_BYTES = 10 * 1024 * 1024;

    private BackupManager() {}

    static String build(DbHelper db) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "F-TikTokManager");
        root.put("version", 2);
        root.put("exported", System.currentTimeMillis());

        JSONArray accounts = new JSONArray();
        for (CloneModel m : db.getAllAccounts()) {
            JSONObject o = new JSONObject();
            o.put("name", m.name);
            o.put("cookies", SessionHelper.hasStored(m.cookies) ? m.cookies : "");
            accounts.put(o);
        }
        root.put("accounts", accounts);

        JSONArray links = new JSONArray();
        for (LinkModel l : db.getLinks()) {
            JSONObject o = new JSONObject();
            o.put("title", l.title);
            o.put("url", l.url);
            links.put(o);
        }
        root.put("links", links);
        return root.toString(2);
    }

    /** Returns number of accounts added. Existing data is never overwritten. */
    static int restore(DbHelper db, String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (!"F-TikTokManager".equals(root.optString("app"))) {
            throw new JSONException("Not a TikTok Manager backup");
        }

        Set<String> names = new HashSet<>();
        List<CloneModel> existing = db.getAllAccounts();
        for (CloneModel m : existing) names.add(m.name);

        int added = 0;
        JSONArray accounts = root.optJSONArray("accounts");
        if (accounts != null) {
            for (int i = 0; i < accounts.length(); i++) {
                JSONObject o = accounts.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name", "Clone").trim();
                if (name.isEmpty()) name = "Clone";
                String unique = name;
                int n = 2;
                while (names.contains(unique)) unique = name + " (" + (n++) + ")";
                names.add(unique);
                db.addAccount(unique, o.optString("cookies", ""));
                added++;
            }
        }

        JSONArray links = root.optJSONArray("links");
        if (links != null) {
            for (int i = 0; i < links.length(); i++) {
                JSONObject o = links.optJSONObject(i);
                if (o == null) continue;
                String url = o.optString("url", "").trim();
                if (!(url.startsWith("http://") || url.startsWith("https://"))) continue;
                if (db.hasLink(url)) continue;
                String title = o.optString("title", url).trim();
                db.addLink(title.isEmpty() ? url : title, url);
            }
        }
        return added;
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) throw new IOException("File too large");
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
