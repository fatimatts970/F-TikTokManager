package com.ftiktokmanager.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WebActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "account_id";
    public static final String EXTRA_NAME = "account_name";
    public static final String EXTRA_URL = "url";
    private WebView webView;
    private ProgressBar progressBar;
    private int accountId;
    private String accountName;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int REQ_CHOOSE_IMG = 101;
    private static final int REQ_VCAM_CAPTURE = 102;
    private static final int REQ_ADD_VCAM_IMG = 201;
    private static final int REQ_WEB_PERM = 301;
    private SharedPreferences sp;

    private DeviceProfile device;
    private String realUa;
    private String deviceScript;
    private boolean docStartOk = false;
    private ScriptHandler docStartHandler;
    private boolean desktop = false;
    private PermissionRequest pendingWebPerm;

    private CloneModel clone;
    private boolean webrtcBlock = false;
    private boolean profileMode = false;
    private CookieManager cm;
    private SocksBridge bridge;
    private static WebActivity proxyOwner;

    private ImageButton btnVcam, btnView;
    private TextView txtHost;

    private static final String[][] QUICK_LINKS = {
            {"\uD83E\uDDFE Tax Info (USA Acc)", "https://www.tiktok.com/tax/us-w9-tax-select?type"},
            {"\uD83D\uDCCA Tax Status (USA Acc)", "https://www.tiktok.com/tax/info-us?enter_from"},
            {"\uD83D\uDCB0 Payout Dashboard", "https://www.tiktok.com/periodic/dashboard"},
            {"\uD83D\uDCB5 Payout / Monthly Earning", "https://www.tiktok.com/reward-onboarding?wallet_type=MONTHLY_EARNING&click_entrance=monthly_earnings_page"},
            {"\uD83C\uDFAC TikTok Studio Upload", "https://www.tiktok.com/tiktokstudio/upload"},
            {"\uD83C\uDF82 Age DOB Verify", "https://www.tiktok.com/tpp/webapp/age-verification/dob.html?object_type=67"},
            {"\uD83D\uDCE1 Age DOB (TikTok Live)", "https://www.tiktok.com/login?enter_from=underage_account&redirect_url=https%3A%2F%2Fwww.tiktok.com%2Ftpp%2Fwebapp%2Fage-verification%2Fdob.html%3Flang%3Den%26object_type%3D69%26object_id%3D"},
            {"\u2705 KYC", "https://www.tiktok.com/kyc"},
            {"\u26A0\uFE0F Report a Problem", "https://www.tiktok.com/legal/report/feedback"},
            {"\uD83C\uDF0D Check My IP (Region Detect)", ""}
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

        accountId = getIntent().getIntExtra(EXTRA_ID, 1);
        accountName = getIntent().getStringExtra(EXTRA_NAME);
        sp = getSharedPreferences("cfg", MODE_PRIVATE);
        device = DeviceProfile.forAccount(accountId);

        try {
            clone = App.db().getAccount(accountId);
            desktop = clone != null && clone.desk == 1;
        } catch (Exception ignored) {
        }

        TextView txtTitle = findViewById(R.id.txtTitle);
        if (accountName != null) {
            txtTitle.setText(accountName);
        }
        txtHost = findViewById(R.id.txtHost);

        progressBar = findViewById(R.id.webProgress);
        webView = findViewById(R.id.webView);
        btnVcam = findViewById(R.id.btnVcam);
        btnView = findViewById(R.id.btnView);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnVcam.setOnClickListener(v -> showVcam());
        findViewById(R.id.btnLinks).setOnClickListener(v -> showQuickLinks());
        findViewById(R.id.btnKyc).setOnClickListener(v -> webView.loadUrl("https://www.tiktok.com/kyc"));
        btnView.setOnClickListener(v -> toggleDesktop());

        setupWebView();
        refreshToolbar();
        String startUrl = getIntent().getStringExtra(EXTRA_URL);
        prepareAndLoad(startUrl != null ? startUrl : "https://www.tiktok.com/");
    }

    /** Own browser profile + own cookies per clone, then the proxy (if any), then the page. */
    private void prepareAndLoad(final String url) {
        final String stored = clone == null ? "" : clone.cookies;
        if (profileMode) {
            SessionHelper.injectStored(cm, stored);
            startProxyThenLoad(url);
        } else {
            // Old WebView without profiles: swap this clone's cookies in
            cm.removeAllCookies(ok -> runOnUiThread(() -> {
                SessionHelper.injectStored(cm, stored);
                startProxyThenLoad(url);
            }));
        }
    }

    private void startProxyThenLoad(final String url) {
        final ProxyStore.Cfg px = ProxyStore.effective(this, clone);
        if (px == null) {
            ProxyCfg.clear();
            webView.loadUrl(url);
            return;
        }
        // A proxy is set for this clone: never load through the phone's own connection
        if (!ProxyCfg.supported()) {
            Toast.makeText(this, "Proxy ke liye \"Android System WebView\" update karo (Play Store). Page load nahi kiya.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            bridge = new SocksBridge(px.host, px.port, px.user, px.pass);
            int port = bridge.start();
            proxyOwner = this;
            ProxyCfg.apply(port, () -> runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, "\uD83C\uDF10 Proxy: " + px.host + ":" + px.port, Toast.LENGTH_SHORT).show();
                webView.loadUrl(url);
            }));
        } catch (Exception e) {
            Toast.makeText(this, "Proxy start nahi hui: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------ toolbar

    private void refreshToolbar() {
        boolean virtual = sp.getBoolean("cam_virtual", false);
        btnVcam.setColorFilter(virtual ? getColor(R.color.accent_pink) : getColor(R.color.icon_primary));
        btnView.setImageResource(desktop ? R.drawable.ic_desktop : R.drawable.ic_phone);
        txtHost.setText((desktop ? "Desktop" : "tiktok.com") + " \u2022 " + device.label());
    }

    private void showVcam() {
        VcamUi.show(this, accountId, () -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            try {
                startActivityForResult(i, REQ_ADD_VCAM_IMG);
            } catch (Exception e) {
                Toast.makeText(this, "Gallery open nahi hui", Toast.LENGTH_SHORT).show();
            }
        }, this::refreshToolbar);
    }

    private void showQuickLinks() {
        String[] labels = new String[QUICK_LINKS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = QUICK_LINKS[i][0];
        new MaterialAlertDialogBuilder(this)
                .setTitle("\uD83D\uDD17 Quick Links")
                .setItems(labels, (d, which) -> {
                    String url = QUICK_LINKS[which][1];
                    if (url.isEmpty()) {
                        Intent i = new Intent(this, IpCheckerActivity.class);
                        i.putExtra("account_id", accountId);
                        i.putExtra("account_name", accountName);
                        startActivity(i);
                    } else {
                        webView.loadUrl(url);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggleDesktop() {
        desktop = !desktop;
        applyMode();
        refreshToolbar();
        final int v = desktop ? 1 : 0;
        App.io(() -> App.db().setDesktop(accountId, v));
        Toast.makeText(this, desktop ? "\uD83D\uDDA5\uFE0F Desktop view ON" : "\uD83D\uDCF1 Mobile view ON",
                Toast.LENGTH_SHORT).show();
        webView.reload();
    }

    // ------------------------------------------------------------ webview

    /** UA, client hints, viewport and fingerprint script for this clone (mobile or desktop). */
    private void applyMode() {
        WebSettings s = webView.getSettings();
        String ua = device.buildUserAgent(realUa, desktop);
        s.setUserAgentString(ua);
        DeviceProfile.applyUaMetadata(s, device, ua, desktop);

        s.setUseWideViewPort(desktop);
        s.setLoadWithOverviewMode(desktop);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(desktop);
        s.setDisplayZoomControls(false);

        deviceScript = device.script(accountId, ua, desktop) + ";" + antiDetectScript()
                + (webrtcBlock ? ";" + webrtcScript() : "");
        if (docStartHandler != null) {
            try {
                docStartHandler.remove();
            } catch (Throwable ignored) {
            }
            docStartHandler = null;
        }
        docStartOk = false;
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                docStartHandler = WebViewCompat.addDocumentStartJavaScript(webView, deviceScript,
                        Collections.singleton("*"));
                docStartOk = true;
            }
        } catch (Throwable ignored) {
            docStartOk = false;
        }
    }

    private void setupWebView() {
        // Each clone = its own browser (cookies, storage, cache). Must happen before the WebView is used.
        // With a proxy in use, WebRTC must not reveal the phone's real IP (both mobile and desktop view)
        webrtcBlock = ProxyStore.effective(this, clone) != null;
        profileMode = SessionHelper.bindProfile(webView, accountId);
        cm = SessionHelper.cookieManager(profileMode, accountId);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        realUa = settings.getUserAgentString();
        applyMode();

        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return blockNonWeb(view, request.getUrl().toString());
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // Safety net: if an app-scheme (snssdk1233:// etc.) ever slips through, don't leave the error page.
                if (request.isForMainFrame() && error.getErrorCode() == WebViewClient.ERROR_UNSUPPORTED_SCHEME) {
                    if (view.canGoBack()) view.goBack();
                    return;
                }
                super.onReceivedError(view, request, error);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                // Runs at document start when supported; this is the fallback (scripts guard against double run)
                if (deviceScript != null) view.evaluateJavascript(deviceScript, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                final String cookies = cm.getCookie(url);
                cm.flush();
                if (cookies != null) {
                    App.io(() -> App.db().updateCookies(accountId, cookies));
                }
            }

            // Local virtual-camera picture: only served while Virtual mode is ON, otherwise 404 -> page uses the real camera
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request.getUrl().toString().contains("vcam.local/stream.jpg")) {
                    Map<String, String> h = new HashMap<>();
                    h.put("Access-Control-Allow-Origin", "*");
                    h.put("Cache-Control", "no-store");
                    byte[] data = null;
                    if (sp.getBoolean("cam_virtual", false)) {
                        try {
                            data = VcamImg.selectedJpeg(WebActivity.this, sp, accountId);
                        } catch (Throwable ignored) {
                        }
                    }
                    if (data != null) {
                        return new WebResourceResponse("image/jpeg", null, 200, "OK", h, new ByteArrayInputStream(data));
                    }
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", h,
                            new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }

            // Real camera / microphone for the page (Physical mode)
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> handleWebPermission(request));
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebPerm == request) pendingWebPerm = null;
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                if (WebActivity.this.filePathCallback != null) {
                    WebActivity.this.filePathCallback.onReceiveValue(null);
                }
                WebActivity.this.filePathCallback = filePathCallback;
                boolean virtual = sp.getBoolean("cam_virtual", false);

                if (virtual && VcamImg.selected(sp, accountId) != null) {
                    // Virtual mode: hand the selected (adjusted) image to the page
                    App.io(() -> {
                        final File out = VcamImg.adjustedFile(WebActivity.this, sp, accountId);
                        App.ui(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (out == null) {
                                cancelChooser();
                                return;
                            }
                            Intent intent = new Intent(WebActivity.this, VcamActivity.class);
                            intent.putExtra("image_uri", Uri.fromFile(out).toString());
                            startActivityForResult(intent, REQ_VCAM_CAPTURE);
                        });
                    });
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

    private void cancelChooser() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
    }

    // ------------------------------------------------------------ permissions

    private void handleWebPermission(PermissionRequest request) {
        List<String> need = new ArrayList<>();
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.CAMERA);
            }
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.RECORD_AUDIO);
            }
        }
        if (need.isEmpty()) {
            request.grant(request.getResources());
        } else {
            pendingWebPerm = request;
            requestPermissions(need.toArray(new String[0]), REQ_WEB_PERM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_WEB_PERM || pendingWebPerm == null) return;
        boolean ok = grantResults.length > 0;
        for (int g : grantResults) if (g != PackageManager.PERMISSION_GRANTED) ok = false;
        try {
            if (ok) pendingWebPerm.grant(pendingWebPerm.getResources());
            else pendingWebPerm.deny();
        } catch (Exception ignored) {
        }
        pendingWebPerm = null;
    }

    // ------------------------------------------------------------ helpers

    /**
     * TikTok pages try to wake the real TikTok app with links like snssdk1233://aweme/detail/...
     * A WebView can't open those (ERR_UNKNOWN_URL_SCHEME), so every non-web link is swallowed
     * and the clone stays on the current page.
     */
    private boolean blockNonWeb(WebView view, String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        if (u.startsWith("http://") || u.startsWith("https://") || u.startsWith("about:")
                || u.startsWith("blob:") || u.startsWith("data:") || u.startsWith("javascript:")
                || u.startsWith("file:")) {
            return false;
        }
        if (u.startsWith("intent:")) {
            try {
                Intent i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                final String fb = i.getStringExtra("browser_fallback_url");
                if (fb != null && (fb.startsWith("http://") || fb.startsWith("https://"))) {
                    view.post(() -> view.loadUrl(fb));
                }
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    /**
     * WebRTC can ask STUN servers over UDP and bypass the proxy, which shows the real IP.
     * Force "relay only, no servers": no candidates are gathered, so nothing leaks.
     */
    private String webrtcScript() {
        return "(function(){try{" +
                "if(window.__frtc)return;window.__frtc=1;" +
                "['RTCPeerConnection','webkitRTCPeerConnection'].forEach(function(n){" +
                "  var O=window[n];if(!O)return;" +
                "  var W=function(cfg,opt){cfg=Object.assign({},cfg||{});cfg.iceServers=[];cfg.iceTransportPolicy='relay';return new O(cfg,opt);};" +
                "  W.prototype=O.prototype;" +
                "  try{W.generateCertificate=O.generateCertificate;}catch(e){}" +
                "  window[n]=W;" +
                "});" +
                "var P=window.RTCPeerConnection&&RTCPeerConnection.prototype;" +
                "if(P&&P.setConfiguration){var sc=P.setConfiguration;" +
                "  P.setConfiguration=function(c){c=Object.assign({},c||{});c.iceServers=[];c.iceTransportPolicy='relay';return sc.call(this,c);};}" +
                "}catch(e){}})();";
    }

    private String antiDetectScript() {
        // getUserMedia: asks the local vcam.local endpoint. Virtual ON -> picture becomes the camera stream.
        // Virtual OFF (404) -> falls back to the REAL camera.
        return "try{Object.defineProperty(navigator, 'webdriver', {get: () => undefined, configurable: true});}catch(e){}" +
                "window.chrome = window.chrome || { runtime: {} };" +
                "(function(){" +
                "  var md = navigator.mediaDevices;" +
                "  if (!md || !md.getUserMedia || window.__fvc) return;" +
                "  window.__fvc = 1;" +
                "  var orig = md.getUserMedia.bind(md);" +
                "  md.getUserMedia = function(c) {" +
                "    if (!c || !c.video) return orig(c);" +
                "    return new Promise(function(res, rej) {" +
                "      var img = new Image();" +
                "      img.crossOrigin = 'anonymous';" +
                "      img.onload = function() {" +
                "        var cv = document.createElement('canvas');" +
                "        cv.width = " + VcamImg.OUT_W + "; cv.height = " + VcamImg.OUT_H + ";" +
                "        var x = cv.getContext('2d');" +
                "        function d() { x.drawImage(img, 0, 0, cv.width, cv.height); }" +
                "        d(); setInterval(d, 33);" +
                "        var st = cv.captureStream(30);" +
                "        if (c.audio) {" +
                "          orig({audio: c.audio}).then(function(a) {" +
                "            a.getAudioTracks().forEach(function(t) { st.addTrack(t); });" +
                "            res(st);" +
                "          }, function() { res(st); });" +
                "        } else { res(st); }" +
                "      };" +
                "      img.onerror = function() { orig(c).then(res, rej); };" +
                "      img.src = 'https://vcam.local/stream.jpg?t=' + Date.now();" +
                "    });" +
                "  };" +
                "})();";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_ADD_VCAM_IMG) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                final Uri src = data.getData();
                App.io(() -> {
                    try {
                        File f = VcamImg.importUri(WebActivity.this, accountId, src);
                        VcamImg.select(sp, accountId, f);
                        sp.edit().putBoolean("cam_virtual", true).apply();
                        App.ui(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            Toast.makeText(this, "\uD83C\uDFAD Image added - Virtual Camera ON", Toast.LENGTH_SHORT).show();
                            refreshToolbar();
                            showVcam();
                        });
                    } catch (Exception e) {
                        App.ui(() -> Toast.makeText(this, "Image add nahi hui", Toast.LENGTH_SHORT).show());
                    }
                });
            } else {
                showVcam();
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

    @Override
    protected void onDestroy() {
        if (bridge != null) {
            bridge.stop();
            bridge = null;
        }
        if (proxyOwner == this) {
            proxyOwner = null;
            ProxyCfg.clear();
        }
        try {
            if (webView != null) webView.destroy();
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
