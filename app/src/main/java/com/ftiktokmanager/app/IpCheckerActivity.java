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
import java.util.TimeZone;

public class IpCheckerActivity extends AppCompatActivity {
    private TextView txtFlag, txtIp, txtCountry, valCity, valIsp, valTz, valDeviceTz, txtStatus;
    private View progress;
    private SharedPreferences sp;
    private String currentIp = "";
    private volatile String modeLine = "";

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
        final int accId = getIntent().getIntExtra("account_id", 0);
        final String accName = getIntent().getStringExtra("account_name");
        App.io(() -> {
            String host = "", user = "", pass = "";
            int port = 0;
            if (accId > 0) {
                CloneModel m = App.db().getAccount(accId);
                ProxyStore.Cfg px = ProxyStore.effective(IpCheckerActivity.this, m);
                if (px != null) {
                    host = px.host;
                    port = px.port;
                    user = px.user;
                    pass = px.pass;
                }
            }
            final boolean viaProxy = !host.isEmpty();
            modeLine = viaProxy
                    ? "🟢 " + (accName == null ? "Account" : accName) + ": through its SOCKS5 proxy"
                    : "⚪ " + (accName == null ? "This phone" : accName) + ": direct connection";
            try {
                final IpTool.Result r = IpTool.fetch(host, port, user, pass);
                if (!viaProxy) {
                    sp.edit().putString("last_ip", r.ip).putString("last_country", r.country)
                            .putLong("last_checked", System.currentTimeMillis()).apply();
                }
                App.ui(() -> show(r.ip, r.country, r.cc, r.region, r.city, r.isp, r.tz));
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? "unknown" : e.getMessage();
                App.ui(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    txtIp.setText("No connection");
                    txtStatus.setText("❌ Could not check.\n\n" + msg
                            + "\n\nInternet ya proxy settings check karke dobara try karo.");
                    txtStatus.setTextColor(getColor(R.color.warning));
                });
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
        String prefix = modeLine.isEmpty() ? "" : modeLine + "\nThis is the connection TikTok sees.\n\n";
        if (tzId.isEmpty()) {
            txtStatus.setText(prefix + "Location details are unavailable right now.");
        } else if (tzId.equals(TimeZone.getDefault().getID())) {
            txtStatus.setText(prefix + "✔ Device timezone matches your IP location.");
            txtStatus.setTextColor(getColor(R.color.success));
        } else {
            txtStatus.setText(prefix + "⚠ Device timezone differs from your IP location. Accounts can look suspicious if this keeps changing.");
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
}
