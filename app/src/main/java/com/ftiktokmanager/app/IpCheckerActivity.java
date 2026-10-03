package com.ftiktokmanager.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.TimeZone;
import org.json.JSONObject;

public class IpCheckerActivity extends AppCompatActivity {
    private TextView txtFlag, txtIp, txtCountry, valCity, valIsp, valTz, valDeviceTz, txtStatus;
    private View progress;
    private SharedPreferences sp;
    private String currentIp = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ip);
        sp = getSharedPreferences("cfg", MODE_PRIVATE);

        txtFlag = findViewById(R.id.txtFlag);
        txtIp = findViewById(R.id.txtIp);
        txtCountry = findViewById(R.id.txtCountry);
        valCity = findViewById(R.id.valCity);
        valIsp = findViewById(R.id.valIsp);
        valTz = findViewById(R.id.valTz);
        valDeviceTz = findViewById(R.id.valDeviceTz);
        txtStatus = findViewById(R.id.txtStatus);
        progress = findViewById(R.id.progress);

        valDeviceTz.setText(TimeZone.getDefault().getID());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnRefresh).setOnClickListener(v -> check());
        findViewById(R.id.btnCopy).setOnClickListener(v -> {
            if (currentIp.isEmpty()) return;
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("ip", currentIp));
            Toast.makeText(this, "IP copied", Toast.LENGTH_SHORT).show();
        });

        check();
    }

    private void check() {
        progress.setVisibility(View.VISIBLE);
        txtIp.setText("Checking…");
        txtStatus.setText("");
        txtStatus.setTextColor(getColor(R.color.text_secondary));
        App.io(() -> {
            try {
                JSONObject j = new JSONObject(httpGet("https://ipwho.is/"));
                if (!j.optBoolean("success", true)) throw new IOException("lookup failed");
                final String ip = j.optString("ip", "");
                final String country = j.optString("country", "");
                final String cc = j.optString("country_code", "");
                final String region = j.optString("region", "");
                final String city = j.optString("city", "");
                JSONObject conn = j.optJSONObject("connection");
                final String isp = conn == null ? "" : conn.optString("isp", conn.optString("org", ""));
                JSONObject tz = j.optJSONObject("timezone");
                final String tzId = tz == null ? "" : tz.optString("id", "");
                sp.edit().putString("last_ip", ip).putString("last_country", country)
                        .putLong("last_checked", System.currentTimeMillis()).apply();
                App.ui(() -> show(ip, country, cc, region, city, isp, tzId));
            } catch (Exception e) {
                try {
                    String ip = new JSONObject(httpGet("https://api.ipify.org?format=json")).optString("ip", "");
                    final String fIp = ip;
                    App.ui(() -> show(fIp, "", "", "", "", "", ""));
                } catch (Exception e2) {
                    App.ui(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        progress.setVisibility(View.GONE);
                        txtIp.setText("No connection");
                        txtStatus.setText("Could not reach the IP lookup service. Check your internet and tap refresh.");
                    });
                }
            }
        });
    }

    private void show(String ip, String country, String cc, String region, String city, String isp, String tzId) {
        if (isFinishing() || isDestroyed()) return;
        progress.setVisibility(View.GONE);
        currentIp = ip;
        txtIp.setText(ip.isEmpty() ? "Unknown" : ip);
        txtFlag.setText(flag(cc));
        txtCountry.setText(country);
        String loc = (city.isEmpty() ? "" : city) + (city.isEmpty() || region.isEmpty() ? "" : ", ") + region;
        valCity.setText(loc.isEmpty() ? "-" : loc);
        valIsp.setText(isp.isEmpty() ? "-" : isp);
        valTz.setText(tzId.isEmpty() ? "-" : tzId);
        if (tzId.isEmpty()) {
            txtStatus.setText("Location details are unavailable right now.");
        } else if (tzId.equals(TimeZone.getDefault().getID())) {
            txtStatus.setText("✔ Device timezone matches your IP location.");
            txtStatus.setTextColor(getColor(R.color.success));
        } else {
            txtStatus.setText("⚠ Device timezone differs from your IP location. Accounts can look suspicious if this keeps changing.");
            txtStatus.setTextColor(getColor(R.color.warning));
        }
    }

    private static String flag(String cc) {
        if (cc == null || cc.length() != 2) return "🌐";
        String u = cc.toUpperCase();
        int a = Character.codePointAt(u, 0) - 'A' + 0x1F1E6;
        int b = Character.codePointAt(u, 1) - 'A' + 0x1F1E6;
        return new String(Character.toChars(a)) + new String(Character.toChars(b));
    }

    private static String httpGet(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "TikTokManager/1.1");
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            return BackupManager.readAll(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }
}
