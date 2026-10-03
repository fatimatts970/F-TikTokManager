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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class WebActivity extends AppCompatActivity {
    private WebView webView;
    private ProgressBar progressBar;
    private int accountId;
    private String accountName;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int REQ_CHOOSE_IMG = 101;
    private static final int REQ_VCAM_CAPTURE = 102;
    private SharedPreferences sp;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

        accountId = getIntent().getIntExtra("account_id", 1);
        accountName = getIntent().getStringExtra("account_name");
        sp = getSharedPreferences("cfg", MODE_PRIVATE);

        TextView txtTitle = findViewById(R.id.txtTitle);
        txtTitle.setText(accountName);

        progressBar = findViewById(R.id.webProgress);
        webView = findViewById(R.id.webView);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnChooseImage).setOnClickListener(v -> pickVirtualImage());

        setupWebView();
        webView.loadUrl("https://www.tiktok.com/");
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
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
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
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

    private void pickVirtualImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, 201);
    }

    private void injectAntiDetect(WebView view) {
        String js = "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
                    "window.chrome = { runtime: {} };";
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
