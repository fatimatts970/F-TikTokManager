package com.ftiktokmanager.app;

import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

/** Points the WebView at the local SocksBridge (127.0.0.1:port) or clears the override. */
final class ProxyCfg {
    private ProxyCfg() {}

    static boolean supported() {
        try {
            return WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE);
        } catch (Throwable t) {
            return false;
        }
    }

    /** No direct fallback: if the proxy dies, requests fail instead of leaking the real IP. */
    static void apply(int localPort, Runnable done) {
        ProxyConfig cfg = new ProxyConfig.Builder()
                .addProxyRule("127.0.0.1:" + localPort)
                .build();
        ProxyController.getInstance().setProxyOverride(cfg, Runnable::run, done);
    }

    static void clear() {
        if (!supported()) return;
        try {
            ProxyController.getInstance().clearProxyOverride(Runnable::run, () -> { });
        } catch (Throwable ignored) {
        }
    }
}
