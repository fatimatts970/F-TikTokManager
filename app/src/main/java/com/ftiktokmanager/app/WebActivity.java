package com.ftiktokmanager.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WebActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "account_id";
    public static final String EXTRA_NAME = "account_name";
    public static final String EXTRA_URL = "start_url";

    private static final String ANTI_DETECT_JS =
            "(function(){try{Object.defineProperty(navigator,'webdriver',{get:function(){return undefined;}});}catch(e){}" +
            "try{if(!window.chrome){window.chrome={runtime:{}};}}catch(e){}})();";

    private WebView webView;
    private ProgressBar progressBar;
    private TextView txtHost;
    private int accountId;
    private String accountName;
    private SharedPreferences sp;

    private boolean profileMode;
    private boolean docStartScript;
    private boolean sessionReady;
    private CookieManager cookieManager;
    private volatile String vcamPath = "";

    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingPermission;

    private final ActivityResultLauncher<Intent> chooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                if (fileCallback == null) return;
                Uri[] uris = WebChromeClient.FileChooserParams.parseResult(r.getResultCode(), r.getData());
                fileCallback.onReceiveValue(uris);
                fileCallback = null;
            });

    private final ActivityResultLauncher<Intent> vcamLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                if (fileCallback == null) return;
                Uri u = (r.getResultCode() == RESULT_OK && r.getData() != null) ? r.getData().getData() : null;
                fileCallback.onReceiveValue(u != null ? new Uri[]{u} : null);
                fileCallback = null;
            });

    private final ActivityResultLauncher<Intent> vcamPickLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK && r.getData() != null && r.getData().getData() != null) {
                    importVcamImage(r.getData().getData());
                }
            });

    private final ActivityResultLauncher<String[]> permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                PermissionRequest req = pendingPermission;
                pendingPermission = null;
                if (req == null) return;
                List<String> grant = new ArrayList<>();
                for (String res : req.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)
                            && Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA))) grant.add(res);
                    else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)
                            && Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO))) grant.add(res);
                }
                if (grant.isEmpty()) req.deny();
                else req.grant(grant.toArray(new String[0]));
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

        accountId = getIntent().getIntExtra(EXTRA_ID, 1);
        accountName = getIntent().getStringExtra(EXTRA_NAME);
        sp = getSharedPreferences("cfg", MODE_PRIVATE);

        TextView txtTitle = findViewById(R.id.txtTitle);
        if (accountName != null) txtTitle.setText(accountName);
        txtHost = findViewById(R.id.txtHost);
        progressBar = findViewById(R.id.webProgress);
        webView = findViewById(R.id.webView);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnChooseImage).setOnClickListener(v -> pickVirtualImage());
        findViewById(R.id.btnMore).setOnClickListener(this::showMenu);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) webView.goBack();
                else finish();
            }
        });

        setupWebView();

        final String startUrl = normalizeUrl(getIntent().getStringExtra(EXTRA_URL));
        App.io(() -> {
            CloneModel m = App.db().getAccount(accountId);
            final String stored = m == null ? "" : m.cookies;
            vcamPath = m == null ? "" : m.vcamPath;
            App.db().touchAccount(accountId);
            App.ui(() -> {
                if (isFinishing() || isDestroyed()) return;
                startSession(stored, startUrl);
            });
        });
    }

    private static String normalizeUrl(String u) {
        if (u == null || u.trim().isEmpty()) return SessionHelper.TIKTOK + "/";
        u = u.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        return u;
    }

    /** Restores this account's session, then loads the page. */
    private void startSession(final String stored, final String url) {
        if (profileMode) {
            SessionHelper.injectStored(cookieManager, stored);
            sessionReady = true;
            webView.loadUrl(url);
        } else {
            // Old WebView: one shared cookie jar -> swap this account's cookies in.
            cookieManager.removeAllCookies(ok -> {
                android.webkit.WebStorage.getInstance().deleteAllData();
                SessionHelper.injectStored(cookieManager, stored);
                sessionReady = true;
                webView.loadUrl(url);
            });
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        // Must happen before anything else touches the WebView.
        profileMode = SessionHelper.bindProfile(webView, accountId);
        cookieManager = SessionHelper.cookieManager(profileMode, accountId);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        // Use the real WebView version/device but without the "; wv" / "Version/4.0" markers.
        String ua = settings.getUserAgentString();
        if (ua != null) {
            settings.setUserAgentString(ua.replace("; wv", "").replace(" Version/4.0", ""));
        }

        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(webView, ANTI_DETECT_JS, Collections.singleton("*"));
                docStartScript = true;
            }
        } catch (Throwable ignored) {
            docStartScript = false;
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                if (!docStartScript) view.evaluateJavascript(ANTI_DETECT_JS, null);
                try {
                    String host = Uri.parse(url).getHost();
                    if (host != null) txtHost.setText(host);
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                saveCookies();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                // Never hand off to the real TikTok app / other apps: it would break account isolation.
                return !("http".equals(scheme) || "https".equals(scheme));
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    Snackbar.make(findViewById(R.id.webRoot), "Page failed to load", Snackbar.LENGTH_LONG)
                            .setAction("Retry", v -> view.reload()).show();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null); // a pending chooser must always be answered
                    fileCallback = null;
                }
                fileCallback = callback;

                boolean vcamOn = sp.getBoolean("cam_virtual", false);
                try {
                    if (vcamOn && acceptsImages(params)) {
                        Intent intent = new Intent(WebActivity.this, VcamActivity.class);
                        intent.putExtra("image_uri", currentVcamUri());
                        vcamLauncher.launch(intent);
                    } else {
                        chooserLauncher.launch(params.createIntent());
                    }
                } catch (Exception e) {
                    callback.onReceiveValue(null);
                    fileCallback = null;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> askWebPermission(request));
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingPermission == request) pendingPermission = null;
            }
        });
    }

    private static boolean acceptsImages(WebChromeClient.FileChooserParams params) {
        String[] types = params.getAcceptTypes();
        if (types == null || types.length == 0) return true;
        for (String t : types) {
            if (t == null || t.isEmpty() || t.startsWith("image") || t.equals("*/*")
                    || t.contains("jpg") || t.contains("jpeg") || t.contains("png")) return true;
        }
        return false;
    }

    private String currentVcamUri() {
        String p = vcamPath;
        if (p == null || p.isEmpty()) return "";
        File f = new File(p);
        return f.exists() ? Uri.fromFile(f).toString() : "";
    }

    // ------------------------------------------------- camera / mic from web

    private void askWebPermission(final PermissionRequest request) {
        final List<String> need = new ArrayList<>();
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) need.add(Manifest.permission.CAMERA);
            else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) need.add(Manifest.permission.RECORD_AUDIO);
        }
        if (need.isEmpty()) {
            request.deny();
            return;
        }
        String host = request.getOrigin() == null ? "This site" : request.getOrigin().getHost();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Permission request")
                .setMessage(host + " wants to use your camera / microphone.")
                .setPositiveButton("Allow", (d, w) -> {
                    pendingPermission = request;
                    permLauncher.launch(need.toArray(new String[0]));
                })
                .setNegativeButton("Deny", (d, w) -> request.deny())
                .setOnCancelListener(d -> request.deny())
                .show();
    }

    // ------------------------------------------------------------------ VCAM

    private void pickVirtualImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            vcamPickLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No image picker found", Toast.LENGTH_SHORT).show();
        }
    }

    /** Copies the picked image into private storage so it still works after the app restarts. */
    private void importVcamImage(final Uri src) {
        App.io(() -> {
            try {
                String mime = getContentResolver().getType(src);
                String ext = "jpg";
                if ("image/png".equals(mime)) ext = "png";
                else if ("image/webp".equals(mime)) ext = "webp";
                VcamStore.deleteFor(getApplicationContext(), accountId);
                File out = new File(VcamStore.dir(getApplicationContext()), "acc_" + accountId + "." + ext);
                try (InputStream in = getContentResolver().openInputStream(src);
                     OutputStream os = new FileOutputStream(out)) {
                    if (in == null) throw new IOException("no stream");
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
                App.db().updateVcam(accountId, out.getAbsolutePath(), 1);
                vcamPath = out.getAbsolutePath();
                sp.edit().putBoolean("cam_virtual", true).apply();
                App.ui(() -> Toast.makeText(this, "VCAM Image Set & Turned ON!", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                App.ui(() -> Toast.makeText(this, "Could not load that image", Toast.LENGTH_LONG).show());
            }
        });
    }

    // ------------------------------------------------------------- menu etc.

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "Reload");
        pm.getMenu().add(0, 2, 1, "Copy link");
        pm.getMenu().add(0, 3, 2, "Clear session (log out)");
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    webView.reload();
                    return true;
                case 2:
                    String u = webView.getUrl();
                    if (u != null) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("link", u));
                        Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                case 3:
                    confirmClear();
                    return true;
                default:
                    return false;
            }
        });
        pm.show();
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear session?")
                .setMessage("This logs " + (accountName == null ? "the account" : accountName) + " out of TikTok.")
                .setPositiveButton("Clear", (d, w) -> {
                    SessionHelper.clearOpenSession(profileMode, accountId, cookieManager);
                    webView.clearCache(true);
                    App.io(() -> App.db().clearCookies(accountId));
                    webView.loadUrl(SessionHelper.TIKTOK + "/");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveCookies() {
        if (cookieManager == null || !sessionReady) return;
        String c = cookieManager.getCookie(SessionHelper.TIKTOK);
        final String cookies = c == null ? "" : c;
        App.io(() -> App.db().updateCookies(accountId, cookies));
        cookieManager.flush();
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onPause() {
        saveCookies();
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        if (webView != null) {
            webView.stopLoading();
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
