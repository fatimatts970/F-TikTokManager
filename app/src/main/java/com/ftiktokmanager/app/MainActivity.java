package com.ftiktokmanager.app;

import android.content.Intent;
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
import java.io.InputStream;
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
        String[] opts = {"Open", "Rename", "Clear session (log out)", "Delete account"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(m.name)
                .setItems(opts, (d, which) -> {
                    if (which == 0) onOpen(m);
                    else if (which == 1) Ui.promptText(this, "Rename account", m.name, name ->
                            App.io(() -> {
                                App.db().renameAccount(m.id, name);
                                loadAccounts();
                            }));
                    else if (which == 2) confirmClear(m);
                    else confirmDelete(m);
                })
                .show();
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
                .setMessage("The backup file contains your logged-in sessions (cookies). Anyone with the file can access those accounts - keep it private.")
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
        TextView iso = v.findViewById(R.id.txtIsolation);
        iso.setText(SessionHelper.profilesSupported()
                ? "Full - every account has its own cookies, storage and cache (WebView multi-profile)."
                : "Partial - only cookies are separated. Update \"Android System WebView\" from Play Store for full isolation.");
        ((TextView) v.findViewById(R.id.txtVersion)).setText("F TikTok Manager v1.1 · Android " + Build.VERSION.RELEASE);
        dlg.setContentView(v);
        dlg.show();
    }

    private void showAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("F TikTok Manager")
                .setMessage("Version 1.1\nIsolated multi-account TikTok web profiles with VCAM image feeding, IP checker, saved links and backup.")
                .setPositiveButton("OK", null)
                .show();
    }
}
