package com.ftiktokmanager.app;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class WebActivity extends AppCompatActivity {

    private WebView webView;
    private ImageView switchIcon;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

        // WebView setup (Facebook load karne ke liye)
        // Note: Agar aapka xml mein ID kuch aur hai toh R.id.webview ki jagah wo likhna
        webView = findViewById(R.id.webview); 
        if (webView != null) {
            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webView.setWebViewClient(new WebViewClient());
            webView.loadUrl("https://m.facebook.com");
        }

        // Camera Switch Icon setup
        // Note: activity_web.xml mein jo icon ka ID hai wo yahan dalein (e.g., R.id.camera_icon)
        switchIcon = findViewById(R.id.camera_icon); 
        if (switchIcon != null) {
            switchIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences prefs = getSharedPreferences("CameraPrefs", MODE_PRIVATE);
                    boolean isVirtualCamOn = prefs.getBoolean("is_virtual_cam", false);

                    // State ko ulta kar dein
                    prefs.edit().putBoolean("is_virtual_cam", !isVirtualCamOn).apply();

                    if (!isVirtualCamOn) {
                        // Virtual Camera Active Ho Gaya
                        Toast.makeText(WebActivity.this, "🎭 Virtual Camera ON", Toast.LENGTH_SHORT).show();
                    } else {
                        // Physical Camera Active Ho Gaya
                        Toast.makeText(WebActivity.this, "📷 Physical Camera ON", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }
}
