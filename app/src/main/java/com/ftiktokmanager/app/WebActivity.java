package com.ftiktokmanager.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class WebActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "account_id";
    public static final String EXTRA_NAME = "account_name";
    public static final String EXTRA_URL = "url";

    private static final String MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final String OFFLINE_BASE = "https://offline.local/";
    private static final String OFFLINE_HTML = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
            + "<body style='font-family:sans-serif;text-align:center;padding:48px 24px;background:#f5f6f8;color:#15171c'>"
            + "<div style='font-size:64px'>📡</div><h2>No Internet Connection</h2>"
            + "<p>Please check your Wi-Fi or mobile data, then tap Retry.</p>"
            + "<a href='https://retry.local/' style='display:inline-block;margin-top:16px;padding:12px 32px;"
            + "background:#fe2c55;color:#fff;border-radius:24px;text-decoration:none;font-weight:bold'>Retry</a>"
            + "</body></html>";

    private WebView webView;
    private ProgressBar progressBar;
    private TextView txtHost;
    private int accountId;
    private String accountName;
    private String startUrl;
    private CloneModel account;
    private boolean desktop;
    private boolean profileMode;
    private CookieManager cm;
    private SocksBridge bridge;
    private ConnectivityManager.NetworkCallback netCb;
    private String lastFailedUrl;
    private boolean showingOffline;
    private PermissionRequest pendingPerm;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int REQ_CHOOSE_IMG = 101;
    private static final int REQ_VCAM_CAPTURE = 102;
    private static final int REQ_WEB_PERM = 103;
    private SharedPreferences sp;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

        accountId = getIntent().getIntExtra(EXTRA_ID, 1);
        accountName = getIntent().getStringExtra(EXTRA_NAME);
        startUrl = getIntent().getStringExtra(EXTRA_URL);
        sp = getSharedPreferences("cfg", MODE_PRIVATE);

        TextView txtTitle = findViewById(R.id.txtTitle);
        if (accountName != null) {
            txtTitle.setText(accountName);
        }
        txtHost = findViewById(R.id.txtHost);

        progressBar = findViewById(R.id.webProgress);
        webView = findViewById(R.id.webView);

        // every account gets its own cookies / storage (must happen before the WebView loads anything)
        profileMode = SessionHelper.bindProfile(webView, accountId);
        cm = SessionHelper.cookieManager(profileMode, accountId);

        findViewById(R.id.btnBack).setOnClickListener(v -> leave());
        findViewById(R.id.btnChooseImage).setOnClickListener(v -> pickVirtualImage());
        findViewById(R.id.btnMore).setOnClickListener(v -> showMenu());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack();
                else leave();
            }
        });

        setupWebView();
        registerNetworkCallback();

        App.io(() -> {
            final CloneModel acc = App.db().getAccount(accountId);
            if (acc != null) App.db().touchAccount(accountId);
            App.ui(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (acc == null) {
                    Toast.makeText(this, "Account not found", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                account = acc;
                begin();
            });
        });
    }

    // ------------------------------------------------------------ start-up flow

    private void begin() {
        desktop = account.desk == 1;
        applyViewMode();
        prepareCookies(this::startProxyThenLoad);
    }

    private void prepareCookies(final Runnable next) {
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);
        if (profileMode) {
            SessionHelper.injectStored(cm, account.cookies);
            next.run();
        } else {
            // old WebView: one shared cookie jar, so swap this account's cookies in
            cm.removeAllCookies(ok -> runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                SessionHelper.injectStored(cm, account.cookies);
                next.run();
            }));
        }
    }

    private void startProxyThenLoad() {
        if (!account.proxyActive()) {
            ProxyCfg.clear();
            loadStart();
            return;
        }
        if (!ProxyCfg.supported()) {
            Toast.makeText(this, "This WebView does not support proxy. Update \"Android System WebView\".", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            bridge = new SocksBridge(account.pxHost, account.pxPort, account.pxUser, account.pxPass);
            int port = bridge.start();
            ProxyCfg.apply(port, () -> runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) loadStart();
            }));
        } catch (IOException e) {
            Toast.makeText(this, "Proxy start failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void loadStart() {
        webView.loadUrl(startUrl != null ? startUrl : "https://www.tiktok.com/");
    }

    private void applyViewMode() {
        WebSettings st = webView.getSettings();
        st.setUserAgentString(desktop ? DESKTOP_UA : MOBILE_UA);
        st.setUseWideViewPort(desktop);
        st.setLoadWithOverviewMode(desktop);
        st.setSupportZoom(true);
        st.setBuiltInZoomControls(true);
        st.setDisplayZoomControls(false);
    }

    // ------------------------------------------------------------------ WebView

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(MOBILE_UA);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                showingOffline = url != null && url.startsWith(OFFLINE_BASE);
                progressBar.setVisibility(View.VISIBLE);
                injectAntiDetect(view);
                updateHost(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                saveSession();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!request.isForMainFrame()) return;
                int c = error.getErrorCode();
                if (c == ERROR_HOST_LOOKUP || c == ERROR_CONNECT || c == ERROR_TIMEOUT
                        || c == ERROR_REDIRECT_LOOP || c == ERROR_UNKNOWN || c == ERROR_IO) {
                    lastFailedUrl = request.getUrl().toString();
                    view.loadDataWithBaseURL(OFFLINE_BASE, OFFLINE_HTML, "text/html", "UTF-8", null);
                }
            }

            // TikTok "open in app" links (snssdk1233://, intent://, tiktok://) are ignored
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme();
                if (scheme == null) return true;
                scheme = scheme.toLowerCase();
                if ("retry.local".equals(u.getHost())) {
                    if (lastFailedUrl != null) view.loadUrl(lastFailedUrl);
                    return true;
                }
                if (scheme.equals("http") || scheme.equals("https") || scheme.equals("about")
                        || scheme.equals("blob") || scheme.equals("data") || scheme.equals("javascript")) {
                    return false;
                }
                if (scheme.equals("intent")) {
                    try {
                        Intent it = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME);
                        String fb = it.getStringExtra("browser_fallback_url");
                        if (fb != null && fb.startsWith("http")) view.loadUrl(fb);
                    } catch (Exception ignored) {
                    }
                }
                return true;
            }

            // YAHAN HAI VIRTUAL CAMERA KA LOCAL STREAM INTERCEPTOR
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request.getUrl().toString().contains("vcam.local/stream.jpg")) {
                    boolean isVcamGlobal = sp.getBoolean("cam_virtual", false);
                    if (isVcamGlobal) {
                        String currentVcam = sp.getString("active_vcam_uri_" + accountId, "");
                        if (!currentVcam.isEmpty()) {
                            try {
                                Uri uri = Uri.parse(currentVcam);
                                InputStream is = getContentResolver().openInputStream(uri);
                                return new WebResourceResponse("image/jpeg", "UTF-8", is);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }

            // real camera / microphone for TikTok (asks the Android permission first if needed)
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    List<String> need = new ArrayList<>();
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)
                                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            need.add(Manifest.permission.CAMERA);
                        }
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)
                                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            need.add(Manifest.permission.RECORD_AUDIO);
                        }
                    }
                    if (need.isEmpty()) {
                        request.grant(request.getResources());
                    } else {
                        if (pendingPerm != null) pendingPerm.deny();
                        pendingPerm = request;
                        requestPermissions(need.toArray(new String[0]), REQ_WEB_PERM);
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                if (WebActivity.this.filePathCallback != null) {
                    WebActivity.this.filePathCallback.onReceiveValue(null);
                }
                WebActivity.this.filePathCallback = filePathCallback;
                boolean isVcamGlobal = sp.getBoolean("cam_virtual", false);

                if (isVcamGlobal) {
                    Intent intent = new Intent(WebActivity.this, VcamActivity.class);
                    String currentVcam = sp.getString("active_vcam_uri_" + accountId, "");
                    intent.putExtra("image_uri", currentVcam);
                    startActivityForResult(intent, REQ_VCAM_CAPTURE);
                    return true;
                }

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, REQ_CHOOSE_IMG);
                } catch (Exception e) {
                    WebActivity.this.filePathCallback = null;
                    return false;
                }
                return true;
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WEB_PERM && pendingPerm != null) {
            boolean ok = grantResults.length > 0;
            for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) ok = false;
            if (ok) pendingPerm.grant(pendingPerm.getResources());
            else pendingPerm.deny();
            pendingPerm = null;
        }
    }

    private void updateHost(String url) {
        if (url == null || txtHost == null) return;
        if (url.startsWith(OFFLINE_BASE)) {
            txtHost.setText("offline");
            return;
        }
        String h = Uri.parse(url).getHost();
        if (h != null) txtHost.setText(h.replaceFirst("^www\\.", ""));
    }

    // ------------------------------------------------------------ session / menu

    private void saveSession() {
        if (cm == null) return;
        try {
            cm.flush();
            final String c = cm.getCookie(SessionHelper.TIKTOK);
            if (c != null && !c.isEmpty()) {
                App.io(() -> App.db().updateCookies(accountId, c));
            }
        } catch (Exception ignored) {
        }
    }

    private void leave() {
        saveSession();
        finish();
    }

    private void reloadPage() {
        if (showingOffline && lastFailedUrl != null) webView.loadUrl(lastFailedUrl);
        else webView.reload();
    }

    private void showMenu() {
        if (account == null) return;
        String[] opts = {
                "🔄 Reload",
                "🔍 Open link / Search",
                "🔗 Quick links",
                desktop ? "📱 Switch to Mobile mode" : "🖥️ Switch to Desktop mode",
                "🌐 SOCKS5 proxy (this account)",
                "🌍 Check IP",
                "🚪 Clear session (log out)"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(accountName == null ? "Account" : accountName)
                .setItems(opts, (d, which) -> {
                    if (which == 0) reloadPage();
                    else if (which == 1) openSearch();
                    else if (which == 2) showQuickLinks();
                    else if (which == 3) toggleDesktop();
                    else if (which == 4) editProxy();
                    else if (which == 5) checkIp();
                    else clearSession();
                })
                .show();
    }

    private void openSearch() {
        Ui.promptText(this, "Open in this clone", "", "Open", text -> {
            String t = text.trim();
            String url;
            if (t.startsWith("http://") || t.startsWith("https://")) url = t;
            else if (t.contains(".") && !t.contains(" ")) url = "https://" + t;
            else url = "https://www.tiktok.com/search?q=" + Uri.encode(t);
            webView.loadUrl(url);
        });
    }

    private void showQuickLinks() {
        App.io(() -> {
            final List<LinkModel> links = App.db().getLinks();
            App.ui(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (links.isEmpty()) {
                    Toast.makeText(this, "No saved links", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] names = new String[links.size()];
                for (int i = 0; i < names.length; i++) names[i] = links.get(i).title;
                new MaterialAlertDialogBuilder(this)
                        .setTitle("🔗 Quick links")
                        .setItems(names, (d, which) -> webView.loadUrl(links.get(which).url))
                        .setNegativeButton("Close", null)
                        .show();
            });
        });
    }

    private void toggleDesktop() {
        desktop = !desktop;
        final int flag = desktop ? 1 : 0;
        account.desk = flag;
        App.io(() -> App.db().setDesktop(accountId, flag));
        applyViewMode();
        reloadPage();
        Toast.makeText(this, desktop ? "🖥️ Desktop mode" : "📱 Mobile mode", Toast.LENGTH_SHORT).show();
    }

    private void editProxy() {
        ProxyDialog.show(this, "SOCKS5 proxy · " + accountName,
                "SOCKS5 proxy only. Each clone can use a different proxy server. "
                        + "Proxy changes apply the next time you open this account.",
                account.pxHost, account.pxPort, account.pxUser, account.pxPass,
                true, account.pxOn == 1, "Use this proxy for " + accountName,
                (h, p, u, pw, on) -> {
                    final int onFlag = on && !h.isEmpty() ? 1 : 0;
                    App.io(() -> App.db().updateProxy(accountId, h, p, u, pw, onFlag));
                    Toast.makeText(this, "🌐 Proxy saved - open this account again to apply", Toast.LENGTH_LONG).show();
                    leave();
                });
    }

    private void checkIp() {
        Intent i = new Intent(this, IpCheckerActivity.class);
        i.putExtra("account_id", accountId);
        i.putExtra("account_name", accountName);
        startActivity(i);
    }

    private void clearSession() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear session?")
                .setMessage(accountName + " will be logged out of TikTok. The account itself stays.")
                .setPositiveButton("Clear", (d, w) -> {
                    SessionHelper.clearOpenSession(profileMode, accountId, cm);
                    App.io(() -> App.db().clearCookies(accountId));
                    account.cookies = "";
                    loadStart();
                    Toast.makeText(this, "Session cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void registerNetworkCallback() {
        try {
            ConnectivityManager cmgr = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            netCb = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed() && showingOffline && lastFailedUrl != null) {
                            webView.loadUrl(lastFailedUrl);
                        }
                    });
                }
            };
            cmgr.registerDefaultNetworkCallback(netCb);
        } catch (Exception ignored) {
            netCb = null;
        }
    }

    // ------------------------------------------------------------------ VCAM (unchanged logic)

    private void pickVirtualImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, 201);
    }

    private void injectAntiDetect(WebView view) {
        // The camera override is only injected while VCAM is ON and an image is set,
        // so with VCAM OFF the phone's real camera works normally.
        boolean vcamOn = sp.getBoolean("cam_virtual", false)
                && !sp.getString("active_vcam_uri_" + accountId, "").isEmpty();
        // YAHAN HAI GETUSERMEDIA OVERRIDE JO IMAGE KO LIVE STREAM BANATA HAI
        String js = "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
                    "window.chrome = { runtime: {} };";
        if (vcamOn) {
            js += "if(navigator.mediaDevices) {" +
                    "  navigator.mediaDevices.getUserMedia = function(c) {" +
                    "    return new Promise((res, rej) => {" +
                    "      let img = new Image();" +
                    "      img.crossOrigin = 'anonymous';" +
                    "      img.src = 'https://vcam.local/stream.jpg?t=' + Date.now();" +
                    "      img.onload = () => {" +
                    "        let canvas = document.createElement('canvas');" +
                    "        canvas.width = 480; canvas.height = 640;" +
                    "        let ctx = canvas.getContext('2d');" +
                    "        setInterval(() => ctx.drawImage(img, 0, 0, 480, 640), 33);" +
                    "        res(canvas.captureStream(30));" +
                    "      };" +
                    "      img.onerror = () => rej(new Error('Vcam not ready'));" +
                    "    });" +
                    "  };" +
                    "}";
        }
        view.evaluateJavascript(js, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 201 && resultCode == Activity.RESULT_OK && data != null) {
            Uri selectedUri = data.getData();
            if (selectedUri != null) {
                sp.edit().putString("active_vcam_uri_" + accountId, selectedUri.toString()).apply();
                sp.edit().putBoolean("cam_virtual", true).apply();
                Toast.makeText(this, "VCAM Image Set & Turned ON!", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (filePathCallback == null) return;

        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri result = data.getData();
            if (result != null) {
                filePathCallback.onReceiveValue(new Uri[]{result});
            } else {
                filePathCallback.onReceiveValue(null);
            }
        } else {
            filePathCallback.onReceiveValue(null);
        }
        filePathCallback = null;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onPause() {
        saveSession();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            if (netCb != null) {
                ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(netCb);
            }
        } catch (Exception ignored) {
        }
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (account != null && account.proxyActive()) ProxyCfg.clear();
        if (bridge != null) bridge.stop();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
