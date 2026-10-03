package com.ftiktokmanager.app;

import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

/**
 * Keeps every account's login/session separate.
 *
 * Full isolation: WebView multi-profile (cookies + localStorage + cache per account).
 * Fallback (old System WebView): cookies are swapped in/out of the database per account.
 */
final class SessionHelper {
    static final String TIKTOK = "https://www.tiktok.com";

    private SessionHelper() {}

    static boolean profilesSupported() {
        try {
            return WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE);
        } catch (Throwable t) {
            return false;
        }
    }

    static String profileName(int accountId) {
        return "clone_" + accountId;
    }

    /** Must be called before the WebView is used. Returns true when bound to its own profile. */
    static boolean bindProfile(WebView wv, int accountId) {
        if (!profilesSupported()) return false;
        try {
            WebViewCompat.setProfile(wv, profileName(accountId));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static CookieManager cookieManager(boolean profileMode, int accountId) {
        if (profileMode) {
            try {
                return ProfileStore.getInstance().getOrCreateProfile(profileName(accountId)).getCookieManager();
            } catch (Throwable ignored) {
            }
        }
        return CookieManager.getInstance();
    }

    static boolean hasStored(String stored) {
        return stored != null && !stored.isEmpty() && !"{}".equals(stored);
    }

    /** Puts saved cookies back (restore from backup / fallback mode). Skips if cookies already exist. */
    static void injectStored(CookieManager cm, String stored) {
        if (!hasStored(stored)) return;
        String current = cm.getCookie(TIKTOK);
        if (current != null && !current.isEmpty()) return;
        for (String part : stored.split(";")) {
            String p = part.trim();
            if (p.indexOf('=') <= 0) continue;
            cm.setCookie(TIKTOK, p + "; Domain=.tiktok.com; Path=/; Secure");
        }
        cm.flush();
    }

    /** Wipes cookies + web storage of an account that is currently open. */
    static void clearOpenSession(boolean profileMode, int accountId, CookieManager cm) {
        cm.removeAllCookies(null);
        cm.flush();
        if (profileMode) {
            try {
                ProfileStore.getInstance().getOrCreateProfile(profileName(accountId)).getWebStorage().deleteAllData();
            } catch (Throwable ignored) {
            }
        } else {
            WebStorage.getInstance().deleteAllData();
        }
    }

    /** Removes the profile of an account that is NOT open (clear session / delete account). */
    static void deleteSession(int accountId) {
        if (!profilesSupported()) return;
        try {
            ProfileStore.getInstance().deleteProfile(profileName(accountId));
        } catch (Throwable ignored) {
        }
    }
}
