package com.ftiktokmanager.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.io.InputStream;
import java.util.ArrayList;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements AccountAdapter.Listener {
    private DrawerLayout drawer;
    private TextView txtSubtitle, txtCount;
    private MaterialButton btnVcamToggle;
    private AccountAdapter adapter;
    private SharedPreferences sp;
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri != null) doExport(uri);
            });

    private final ActivityResultLauncher<String[]> permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), r -> { });

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) doImport(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sp = getSharedPreferences("cfg", MODE_PRIVATE);
        drawer = findViewById(R.id.drawerLayout);
        txtSubtitle = findViewById(R.id.txtSubtitle);
        txtCount = findViewById(R.id.txtCount);
        btnVcamToggle = findViewById(R.id.btnVcamToggle);

        RecyclerView rv = findViewById(R.id.rvAccounts);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        rv.setNestedScrollingEnabled(false);
        adapter = new AccountAdapter(this);
        rv.setAdapter(adapter);

        findViewById(R.id.btnMenu).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        findViewById(R.id.btnGlobe).setOnClickListener(v -> openIp());
        findViewById(R.id.btnTune).setOnClickListener(v -> showSettings());
        findViewById(R.id.tileIp).setOnClickListener(v -> openIp());
        findViewById(R.id.tileLinks).setOnClickListener(v -> openLinks());
        findViewById(R.id.tileBackup).setOnClickListener(v -> confirmBackup());
        findViewById(R.id.tileRestore).setOnClickListener(v -> importLauncher.launch(new String[]{"*/*"}));

        btnVcamToggle.setOnClickListener(v -> {
            boolean current = sp.getBoolean("cam_virtual", false);
            sp.edit().putBoolean("cam_virtual", !current).apply();
            updateVcamButtonState();
        });

        NavigationView nav = findViewById(R.id.navView);
        nav.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            drawer.closeDrawer(GravityCompat.START);
            if (id == R.id.nav_ip) openIp();
            else if (id == R.id.nav_links) openLinks();
            else if (id == R.id.nav_vcam) showSettings();
            else if (id == R.id.nav_backup) confirmBackup();
            else if (id == R.id.nav_restore) importLauncher.launch(new String[]{"*/*"});
            else if (id == R.id.nav_about) showAbout();
            return id == R.id.nav_accounts;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        updateVcamButtonState();
        requestBasicPermissions();
        showLastCrash();
    }

    private void requestBasicPermissions() {
        List<String> need = new ArrayList<>();
        need.add(Manifest.permission.CAMERA);
        need.add(Manifest.permission.RECORD_AUDIO);
        need.add(Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE);
        List<String> missing = new ArrayList<>();
        for (String p : need) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (!missing.isEmpty()) permLauncher.launch(missing.toArray(new String[0]));
    }

    private void showLastCrash() {
        final String crash = App.readCrash();
        if (crash == null) return;
        String shown = crash.length() > 1500 ? crash.substring(0, 1500) + "…" : crash;
        new MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ Pichli ghalti")
                .setMessage("App pichli dafa band hua tha. Wajah:\n\n" + shown)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateVcamButtonState();
        loadAccounts();
    }

    // ------------------------------------------------------------------ data

    private void loadAccounts() {
        App.io(() -> {
            DbHelper db = App.db();
            List<CloneModel> list = db.getAllAccounts();
            if (list.isEmpty() && !sp.getBoolean("seeded", false)) {
                db.addAccount("Clone 1");
                list = db.getAllAccounts();
            }
            sp.edit().putBoolean("seeded", true).apply();
            final List<CloneModel> result = list;
            App.ui(() -> {
                if (isFinishing() || isDestroyed()) return;
                adapter.submit(result, deviceLine());
                txtCount.setText(String.valueOf(result.size()));
                int n = result.size();
                txtSubtitle.setText(n + (n == 1 ? " account" : " accounts") + " · backup " + backupText());
            });
        });
    }

    private String deviceLine() {
        String country = sp.getString("last_country", "");
        return Build.MODEL + " · " + (country.isEmpty() ? "IP not checked" : country);
    }

    private String backupText() {
        long t = sp.getLong("last_backup", 0L);
        return t == 0L ? "never" : fmt.format(new Date(t));
    }

    private void updateVcamButtonState() {
        boolean vcam = sp.getBoolean("cam_virtual", false);
        btnVcamToggle.setText(vcam ? "🎭 VCAM: ON" : "📷 VCAM: OFF");
    }

    // --------------------------------------------------------------- actions

    private void openIp() {
        startActivity(new Intent(this, IpCheckerActivity.class));
    }

    private void openLinks() {
        startActivity(new Intent(this, LinksActivity.class));
    }

    @Override
    public void onAdd() {
        App.io(() -> {
            List<CloneModel> l = App.db().getAllAccounts();
            int n = 1;
            while (hasName(l, "Clone " + n)) n++;
            final String def = "Clone " + n;
            App.ui(() -> Ui.promptText(this, "New account", def, name ->
                    App.io(() -> {
                        App.db().addAccount(name);
                        loadAccounts();
                    })));
        });
    }

    private static boolean hasName(List<CloneModel> l, String name) {
        for (CloneModel m : l) if (name.equals(m.name)) return true;
        return false;
    }

    @Override
    public void onOpen(CloneModel m) {
        Ui.launch(this, m, null);
    }

    @Override
    public void onMore(final CloneModel m) {
        String[] opts = {
                "Open",
                "Rename",
                "🔑 Login Details",
                m.desk == 1 ? "🖥️ Desktop Mode: Turn OFF" : "📱 Mobile Mode: Switch to Desktop",
                "🌐 SOCKS5 Proxy (" + (m.proxyActive() ? "ON" : "OFF") + ")",
                "Clear session (log out)",
                "Delete account"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(m.name)
                .setItems(opts, (d, which) -> {
                    if (which == 0) onOpen(m);
                    else if (which == 1) Ui.promptText(this, "Rename account", m.name, name ->
                            App.io(() -> {
                                App.db().renameAccount(m.id, name);
                                loadAccounts();
                            }));
                    else if (which == 2) loginDetails(m);
                    else if (which == 3) toggleDesktop(m);
                    else if (which == 4) accountProxy(m);
                    else if (which == 5) confirmClear(m);
                    else confirmDelete(m);
                })
                .show();
    }

    private void toggleDesktop(final CloneModel m) {
        final int next = m.desk == 1 ? 0 : 1;
        App.io(() -> {
            App.db().setDesktop(m.id, next);
            loadAccounts();
        });
        Toast.makeText(this, next == 1 ? "🖥️ Desktop mode ON for " + m.name : "📱 Mobile mode ON for " + m.name,
                Toast.LENGTH_SHORT).show();
    }

    private EditText loginField(String hint, String value, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(type);
        return e;
    }

    private void loginDetails(final CloneModel m) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 24);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        final EditText eMail = loginField("Email", m.email, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        final EditText eUser = loginField("Username", m.username, InputType.TYPE_CLASS_TEXT);
        final EditText ePass = loginField("Password", m.password, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText eNotes = loginField("Notes", m.notes, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        eNotes.setMinLines(2);
        box.addView(eMail);
        box.addView(eUser);
        box.addView(ePass);
        box.addView(eNotes);
        new MaterialAlertDialogBuilder(this)
                .setTitle("🔑 Login Details")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    final String em = eMail.getText().toString().trim();
                    final String us = eUser.getText().toString().trim();
                    final String pw = ePass.getText().toString();
                    final String no = eNotes.getText().toString().trim();
                    App.io(() -> {
                        App.db().updateLogin(m.id, em, us, pw, no);
                        loadAccounts();
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void accountProxy(final CloneModel m) {
        ProxyDialog.show(this, "SOCKS5 proxy · " + m.name,
                "SOCKS5 proxy only. Each clone can use a different proxy server (one USA, another UK, etc.). "
                        + "No proxy? Switching on any VPN app does the same job.",
                m.pxHost, m.pxPort, m.pxUser, m.pxPass, true, m.pxOn == 1, "Use this proxy for " + m.name,
                (h, p, u, pw, on) -> {
                    final int onFlag = on && !h.isEmpty() ? 1 : 0;
                    App.io(() -> {
                        App.db().updateProxy(m.id, h, p, u, pw, onFlag);
                        loadAccounts();
                    });
                    Toast.makeText(this, "🌐 Proxy settings saved for " + m.name, Toast.LENGTH_SHORT).show();
                });
    }

    private void defaultProxy() {
        ProxyDialog.show(this, "Default SOCKS5 proxy",
                "New accounts will start with these SOCKS5 settings. "
                        + "No proxy? Switching on any VPN app does the same job.",
                sp.getString("px_host", ""), sp.getInt("px_port", 0), sp.getString("px_user", ""),
                sp.getString("px_pass", ""), false, false, null,
                (h, p, u, pw, on) -> {
                    sp.edit().putString("px_host", h).putInt("px_port", p)
                            .putString("px_user", u).putString("px_pass", pw).apply();
                    Toast.makeText(this, "Default proxy saved - new accounts will use these settings",
                            Toast.LENGTH_LONG).show();
                });
    }

    private void confirmClear(final CloneModel m) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear session?")
                .setMessage(m.name + " will be logged out of TikTok. The account itself stays.")
                .setPositiveButton("Clear", (d, w) -> {
                    SessionHelper.deleteSession(m.id);
                    App.io(() -> App.db().clearCookies(m.id));
                    Toast.makeText(this, "Session cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(final CloneModel m) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + m.name + "?")
                .setMessage("This removes the account, its saved session and its VCAM image.")
                .setPositiveButton("Delete", (d, w) -> {
                    SessionHelper.deleteSession(m.id);
                    App.io(() -> {
                        App.db().deleteAccount(m.id);
                        VcamStore.deleteFor(getApplicationContext(), m.id);
                        loadAccounts();
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------------------------------------------------------- backup

    private void confirmBackup() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Create backup")
                .setMessage("The backup file contains your logged-in sessions (cookies), saved login details and proxy settings. Anyone with the file can access those accounts - keep it private. Tip: choose Google Drive in the save picker.")
                .setPositiveButton("Continue", (d, w) ->
                        exportLauncher.launch("tiktok-manager-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".json"))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doExport(final android.net.Uri uri) {
        App.io(() -> {
            try {
                String json = BackupManager.build(App.db());
                try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
                    if (os == null) throw new java.io.IOException("Cannot write file");
                    os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                sp.edit().putLong("last_backup", System.currentTimeMillis()).apply();
                App.ui(() -> Toast.makeText(this, "Backup saved", Toast.LENGTH_SHORT).show());
                loadAccounts();
            } catch (Exception e) {
                App.ui(() -> Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void doImport(final android.net.Uri uri) {
        App.io(() -> {
            try {
                String json;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new java.io.IOException("Cannot read file");
                    json = BackupManager.readAll(in);
                }
                final int added = BackupManager.restore(App.db(), json);
                App.ui(() -> Toast.makeText(this, "Restored " + added + " account(s)", Toast.LENGTH_LONG).show());
                loadAccounts();
            } catch (Exception e) {
                App.ui(() -> Toast.makeText(this, "Restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // -------------------------------------------------------------- settings

    private void showSettings() {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.sheet_settings, null);
        SwitchMaterial sw = v.findViewById(R.id.switchVcam);
        sw.setChecked(sp.getBoolean("cam_virtual", false));
        sw.setOnCheckedChangeListener((b, on) -> {
            sp.edit().putBoolean("cam_virtual", on).apply();
            updateVcamButtonState();
        });
        v.findViewById(R.id.btnDefaultProxy).setOnClickListener(b -> {
            dlg.dismiss();
            defaultProxy();
        });
        TextView iso = v.findViewById(R.id.txtIsolation);
        iso.setText(SessionHelper.profilesSupported()
                ? "Full - every account has its own cookies, storage and cache (WebView multi-profile)."
                : "Partial - only cookies are separated. Update \"Android System WebView\" from Play Store for full isolation.");
        ((TextView) v.findViewById(R.id.txtVersion)).setText("F TikTok Manager v1.2 · Android " + Build.VERSION.RELEASE);
        dlg.setContentView(v);
        dlg.show();
    }

    private void showAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("F TikTok Manager")
                .setMessage("Version 1.2\nIsolated multi-account TikTok web profiles with VCAM image feeding, IP checker, saved links and backup.")
                .setPositiveButton("OK", null)
                .show();
    }
}
