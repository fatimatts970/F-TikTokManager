package com.ftiktokmanager.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Saved SOCKS5 proxies (the list on the PROXY page) + which one is active for ALL clones.
 * A clone that has its own proxy (long-press the clone -> SOCKS5 Proxy) keeps using its own.
 */
final class ProxyStore {
    private static final String KEY_LIST = "proxy_list";
    private static final String KEY_ACTIVE = "proxy_active_id";

    static final class Item {
        int id;
        String host = "";
        int port = 8080;
        String user = "";
        String pass = "";

        String label() {
            return host + ":" + port;
        }
    }

    static final class Cfg {
        final String host;
        final int port;
        final String user;
        final String pass;

        Cfg(String host, int port, String user, String pass) {
            this.host = host;
            this.port = port;
            this.user = user == null ? "" : user;
            this.pass = pass == null ? "" : pass;
        }
    }

    private ProxyStore() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    static List<Item> load(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(KEY_LIST, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.id = o.optInt("id");
                it.host = o.optString("host", "");
                it.port = o.optInt("port", 8080);
                it.user = o.optString("user", "");
                it.pass = o.optString("pass", "");
                if (it.id > 0 && !it.host.isEmpty()) out.add(it);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void save(Context c, List<Item> list) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : list) {
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("host", it.host);
                o.put("port", it.port);
                o.put("user", it.user);
                o.put("pass", it.pass);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        sp(c).edit().putString(KEY_LIST, a.toString()).apply();
    }

    static Item add(Context c, String host, int port, String user, String pass) {
        List<Item> list = load(c);
        int next = 1;
        for (Item it : list) next = Math.max(next, it.id + 1);
        Item n = new Item();
        n.id = next;
        n.host = host;
        n.port = port;
        n.user = user;
        n.pass = pass;
        list.add(n);
        save(c, list);
        return n;
    }

    static void update(Context c, int id, String host, int port, String user, String pass) {
        List<Item> list = load(c);
        for (Item it : list) {
            if (it.id == id) {
                it.host = host;
                it.port = port;
                it.user = user;
                it.pass = pass;
            }
        }
        save(c, list);
    }

    static void delete(Context c, int id) {
        List<Item> list = load(c);
        List<Item> keep = new ArrayList<>();
        for (Item it : list) if (it.id != id) keep.add(it);
        save(c, keep);
        if (activeId(c) == id) setActive(c, 0);
    }

    static int activeId(Context c) {
        return sp(c).getInt(KEY_ACTIVE, 0);
    }

    static void setActive(Context c, int id) {
        sp(c).edit().putInt(KEY_ACTIVE, id).apply();
    }

    /** The proxy a clone really uses: its own one, else the active one from the list, else null (no proxy). */
    static Cfg effective(Context c, CloneModel m) {
        if (m != null && m.proxyActive()) return new Cfg(m.pxHost, m.pxPort, m.pxUser, m.pxPass);
        int act = activeId(c);
        if (act > 0) {
            for (Item it : load(c)) {
                if (it.id == act) return new Cfg(it.host, it.port, it.user, it.pass);
            }
        }
        return null;
    }
}
