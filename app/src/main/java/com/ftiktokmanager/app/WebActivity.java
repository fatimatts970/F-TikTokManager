package com.ftiktokmanager.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceError;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.util.Collections;
import java.util.Locale;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.InputStream;

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
    private SharedPreferences sp;
    private DeviceProfile device;
    private String deviceScript;
    private boolean docStartOk = false;
    private static final String KYC_URL = "https://www.tiktok.com/kyc";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

        accountId = getIntent().getIntExtra(EXTRA_ID, 1);
        accountName = getIntent().getStringExtra(EXTRA_NAME);
        sp = getSharedPreferences("cfg", MODE_PRIVATE);

        TextView txtTitle = findViewById(R.id.txtTitle);
        if (accountName != null) {
            txtTitle.setText(accountName);
        }

        progressBar = findViewById(R.id.webProgress);
        webView = findViewById(R.id.webView);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnChooseImage).setOnClickListener(v -> pickVirtualImage());
        findViewById(R.id.btnKyc).setOnClickListener(v -> webView.loadUrl(KYC_URL));

        device = DeviceProfile.forAccount(accountId);
        TextView txtHost = findViewById(R.id.txtHost);
        if (txtHost != null) txtHost.setText("tiktok.com \u2022 " + device.label());

        setupWebView();
        String startUrl = getIntent().getStringExtra(EXTRA_URL);
        webView.loadUrl(startUrl != null ? startUrl : "https://www.tiktok.com/");
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Each clone = its own phone. Real WebView UA, only the "(Linux; ...)" device part is swapped.
        String finalUa = device.buildUserAgent(settings.getUserAgentString());
        settings.setUserAgentString(finalUa);
        DeviceProfile.applyUaMetadata(settings, device, finalUa);
        deviceScript = device.script(accountId, finalUa);
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(webView, deviceScript, Collections.singleton("*"));
                docStartOk = true;
            }
        } catch (Throwable ignored) {
            docStartOk = false;
        }

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

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
                if (!docStartOk && deviceScript != null) view.evaluateJavascript(deviceScript, null);
                injectAntiDetect(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) {
                    new DbHelper(WebActivity.this).updateCookies(accountId, cookies);
                }
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

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
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
                    return false;
                }
                return true;
            }
        });
    }

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

    private void pickVirtualImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, 201);
    }

    private void injectAntiDetect(WebView view) {
        // YAHAN HAI GETUSERMEDIA OVERRIDE JO IMAGE KO LIVE STREAM BANATA HAI
        String js = "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
                    "window.chrome = { runtime: {} };" +
                    "if(navigator.mediaDevices) {" +
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
}
