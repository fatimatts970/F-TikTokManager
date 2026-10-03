package com.ftiktokmanager.app;

import android.webkit.WebSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gives every clone its own phone identity (model, Android version, screen, GPU, cores, RAM...).
 * The profile is picked from the account id, so a clone always looks like the same phone.
 */
final class DeviceProfile {
    final String model;
    final String brand;
    final int android;
    final int width;      // CSS px
    final int height;     // CSS px
    final float dpr;
    final int cores;
    final int ram;        // GB
    final String gpuVendor;
    final String gpuRenderer;

    private DeviceProfile(String brand, String model, int android, int w, int h, float dpr,
                          int cores, int ram, String gpuVendor, String gpuRenderer) {
        this.brand = brand;
        this.model = model;
        this.android = android;
        this.width = w;
        this.height = h;
        this.dpr = dpr;
        this.cores = cores;
        this.ram = ram;
        this.gpuVendor = gpuVendor;
        this.gpuRenderer = gpuRenderer;
    }

    private static final DeviceProfile[] ALL = {
            new DeviceProfile("Google", "Pixel 8", 14, 412, 915, 2.625f, 8, 8, "Google Inc. (ARM)", "ANGLE (ARM, Mali-G715-Immortalis MC10, OpenGL ES 3.2)"),
            new DeviceProfile("samsung", "SM-S911B", 14, 360, 780, 3f, 8, 8, "Google Inc. (Qualcomm)", "ANGLE (Qualcomm, Adreno (TM) 740, OpenGL ES 3.2)"),
            new DeviceProfile("Google", "Pixel 8 Pro", 14, 412, 915, 2.625f, 8, 12, "Google Inc. (ARM)", "ANGLE (ARM, Mali-G715-Immortalis MC10, OpenGL ES 3.2)"),
            new DeviceProfile("samsung", "SM-A546B", 14, 360, 780, 3f, 8, 8, "Google Inc. (ARM)", "ANGLE (ARM, Mali-G68 MP5, OpenGL ES 3.2)"),
            new DeviceProfile("OnePlus", "CPH2581", 14, 360, 792, 3f, 8, 12, "Google Inc. (Qualcomm)", "ANGLE (Qualcomm, Adreno (TM) 750, OpenGL ES 3.2)"),
            new DeviceProfile("Xiaomi", "23124RA7EO", 13, 393, 873, 2.75f, 8, 8, "Google Inc. (ARM)", "ANGLE (ARM, Mali-G57 MC2, OpenGL ES 3.2)"),
            new DeviceProfile("Google", "Pixel 7", 13, 412, 915, 2.625f, 8, 8, "Google Inc. (ARM)", "ANGLE (ARM, Mali-G710 MP10, OpenGL ES 3.2)"),
            new DeviceProfile("samsung", "SM-S921B", 14, 360, 780, 3f, 8, 8, "Google Inc. (Qualcomm)", "ANGLE (Qualcomm, Adreno (TM) 750, OpenGL ES 3.2)"),
            new DeviceProfile("Google", "Pixel 9", 14, 412, 915, 2.625f, 8, 12, "Google Inc. (ARM)", "ANGLE (ARM, Mali-G715-Immortalis MC10, OpenGL ES 3.2)"),
            new DeviceProfile("samsung", "SM-A156B", 14, 360, 780, 3f, 8, 4, "Google Inc. (ARM)", "ANGLE (ARM, Mali-G68 MP5, OpenGL ES 3.2)"),
            new DeviceProfile("OPPO", "CPH2565", 14, 360, 800, 3f, 8, 8, "Google Inc. (Qualcomm)", "ANGLE (Qualcomm, Adreno (TM) 720, OpenGL ES 3.2)"),
            new DeviceProfile("Xiaomi", "2312DRA50G", 13, 393, 873, 2.75f, 8, 6, "Google Inc. (ARM)", "ANGLE (ARM, Mali-G52 MC2, OpenGL ES 3.2)")
    };

    static DeviceProfile forAccount(int accountId) {
        int i = (Math.abs(accountId) - 1 + ALL.length) % ALL.length;
        return ALL[i];
    }

    /** Short label, e.g. "Pixel 8 · Android 14". */
    String label() {
        return model + " \u00B7 Android " + android;
    }

    private static final Pattern LINUX = Pattern.compile("\\(Linux;[^)]*\\)");
    private static final Pattern CHROME = Pattern.compile("Chrome/(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)");

    /** Takes the REAL WebView user-agent and only swaps the "(Linux; ...)" part for this phone. Desktop = Windows Chrome. */
    String buildUserAgent(String realUa, boolean desktop) {
        String ua = realUa == null ? "" : realUa;
        if (desktop) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
                    + chromeMajor(ua) + ".0.0.0 Safari/537.36";
        }
        String part = "(Linux; Android " + android + "; " + model + ")";
        Matcher m = LINUX.matcher(ua);
        if (m.find()) return m.replaceFirst(Matcher.quoteReplacement(part));
        return "Mozilla/5.0 " + part + " AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    }

    private static String chromeFull(String ua) {
        Matcher m = CHROME.matcher(ua == null ? "" : ua);
        return m.find() ? m.group(1) + "." + m.group(2) + "." + m.group(3) + "." + m.group(4) : "126.0.0.0";
    }

    private static String chromeMajor(String ua) {
        String f = chromeFull(ua);
        return f.substring(0, f.indexOf('.'));
    }

    /** Also change the Client-Hints (Sec-CH-UA-*) the WebView sends. Uses reflection so it can never break the build. */
    static void applyUaMetadata(WebSettings s, DeviceProfile p, String finalUa, boolean desktop) {
        try {
            Class<?> bvB = Class.forName("androidx.webkit.UserAgentMetadata$BrandVersion$Builder");
            Class<?> bvC = Class.forName("androidx.webkit.UserAgentMetadata$BrandVersion");
            Class<?> umB = Class.forName("androidx.webkit.UserAgentMetadata$Builder");
            Class<?> umC = Class.forName("androidx.webkit.UserAgentMetadata");
            Class<?> compat = Class.forName("androidx.webkit.WebSettingsCompat");

            String major = chromeMajor(finalUa);
            String full = chromeFull(finalUa);
            String[][] brands = {{"Chromium", major}, {"Google Chrome", major}, {"Not.A/Brand", "24"}};
            List<Object> list = new ArrayList<>();
            for (String[] b : brands) {
                Object bb = bvB.getConstructor().newInstance();
                bvB.getMethod("setBrand", String.class).invoke(bb, b[0]);
                bvB.getMethod("setMajorVersion", String.class).invoke(bb, b[1]);
                bvB.getMethod("setFullVersion", String.class).invoke(bb, b[1].equals(major) ? full : b[1] + ".0.0.0");
                list.add(bvB.getMethod("build").invoke(bb));
            }
            Object ub = umB.getConstructor().newInstance();
            umB.getMethod("setBrandVersionList", List.class).invoke(ub, list);
            umB.getMethod("setFullVersion", String.class).invoke(ub, full);
            umB.getMethod("setPlatform", String.class).invoke(ub, desktop ? "Windows" : "Android");
            umB.getMethod("setPlatformVersion", String.class).invoke(ub, desktop ? "10.0.0" : p.android + ".0.0");
            umB.getMethod("setArchitecture", String.class).invoke(ub, desktop ? "x86" : "");
            umB.getMethod("setModel", String.class).invoke(ub, desktop ? "" : p.model);
            umB.getMethod("setMobile", boolean.class).invoke(ub, !desktop);
            umB.getMethod("setBitness", int.class).invoke(ub, 64);
            Object meta = umB.getMethod("build").invoke(ub);
            compat.getMethod("setUserAgentMetadata", WebSettings.class, umC).invoke(null, s, meta);
        } catch (Throwable ignored) {
            // Older WebView / API missing -> JS spoof below still covers navigator.userAgentData
        }
    }

    private static String js(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    /** JavaScript that makes the page see this clone's phone. Runs at document start (or onPageStarted as fallback). */
    String script(int accountId, String finalUa, boolean desktop) {
        String major = chromeMajor(finalUa);
        String full = chromeFull(finalUa);
        final String platform = desktop ? "Win32" : "Linux armv8l";
        final int touch = desktop ? 0 : 5;
        final int sw = desktop ? 1920 : width;
        final int sh = desktop ? 1080 : height;
        final int sah = desktop ? 1040 : height - 24;
        final String dprS = desktop ? "1" : String.valueOf(dpr);
        final String uadPlat = desktop ? "Windows" : "Android";
        final String uadMobile = desktop ? "false" : "true";
        final String uadPlatVer = desktop ? "10.0.0" : android + ".0.0";
        final String uadModel = desktop ? "" : model;
        final String uadArch = desktop ? "x86" : "";
        final String gV = desktop ? "Google Inc. (NVIDIA)" : gpuVendor;
        final String gR = desktop ? "ANGLE (NVIDIA, NVIDIA GeForce GTX 1650 Direct3D11 vs_5_0 ps_5_0, D3D11)" : gpuRenderer;
        String out = "(function(){try{" +
                "if(window.__fdev)return;window.__fdev=1;" +
                "var SEED=" + (accountId * 7919 + 104729) + ";" +
                "function rnd(){SEED=(SEED*1103515245+12345)&0x7fffffff;return SEED/0x7fffffff;}" +
                "function def(o,k,v){try{Object.defineProperty(o,k,{get:function(){return v;},configurable:true});}catch(e){}}" +
                // navigator basics
                "var N=Navigator.prototype;" +
                "def(N,'platform','" + platform + "');" +
                "def(N,'hardwareConcurrency'," + cores + ");" +
                "def(N,'deviceMemory'," + Math.min(ram, 8) + ");" +
                "def(N,'maxTouchPoints'," + touch + ");" +
                "def(N,'vendor','Google Inc.');" +
                // screen
                "var S=Screen.prototype;" +
                "def(S,'width'," + sw + ");def(S,'height'," + sh + ");" +
                "def(S,'availWidth'," + sw + ");def(S,'availHeight'," + sah + ");" +
                "def(S,'colorDepth',24);def(S,'pixelDepth',24);" +
                "def(window,'devicePixelRatio'," + dprS + ");" +
                // client hints in JS
                "var BR=[{brand:'Chromium',version:'" + major + "'},{brand:'Google Chrome',version:'" + major + "'},{brand:'Not.A/Brand',version:'24'}];" +
                "var UAD={brands:BR,mobile:" + uadMobile + ",platform:'" + uadPlat + "'," +
                "getHighEntropyValues:function(h){return Promise.resolve({brands:BR,mobile:" + uadMobile + ",platform:'" + uadPlat + "',platformVersion:'" + uadPlatVer + "'," +
                "model:'" + js(uadModel) + "',architecture:'" + uadArch + "',bitness:'64',uaFullVersion:'" + full + "',wow64:false," +
                "fullVersionList:[{brand:'Chromium',version:'" + full + "'},{brand:'Google Chrome',version:'" + full + "'},{brand:'Not.A/Brand',version:'24.0.0.0'}]});}," +
                "toJSON:function(){return {brands:BR,mobile:" + uadMobile + ",platform:'" + uadPlat + "'};}};" +
                "def(N,'userAgentData',UAD);" +
                // WebGL vendor / renderer
                "function gl(P){if(!P)return;var o=P.getParameter;P.getParameter=function(p){" +
                "if(p===37445)return '" + js(gV) + "';if(p===37446)return '" + js(gR) + "';return o.apply(this,arguments);};}" +
                "gl(window.WebGLRenderingContext&&WebGLRenderingContext.prototype);" +
                "gl(window.WebGL2RenderingContext&&WebGL2RenderingContext.prototype);" +
                // canvas noise (same clone = same noise, other clone = different)
                "var ox=rnd()*3|0,oy=rnd()*3|0,od=(rnd()*2|0)?1:-1;" +
                "function noisy(c){try{var t=document.createElement('canvas');t.width=c.width;t.height=c.height;" +
                "if(!t.width||!t.height||t.width*t.height>4000000)return null;" +
                "var x=t.getContext('2d');x.drawImage(c,0,0);var d=gid.call(x,0,0,t.width,t.height);" +
                "for(var i=(oy*t.width+ox)*4;i<d.data.length;i+=4*613){d.data[i]=(d.data[i]+od+256)&255;}" +
                "x.putImageData(d,0,0);return t;}catch(e){return null;}}" +
                "var gid=CanvasRenderingContext2D.prototype.getImageData;" +
                "var tdu=HTMLCanvasElement.prototype.toDataURL,tb=HTMLCanvasElement.prototype.toBlob;" +
                "HTMLCanvasElement.prototype.toDataURL=function(){var n=noisy(this);return tdu.apply(n||this,arguments);};" +
                "HTMLCanvasElement.prototype.toBlob=function(){var n=noisy(this);return tb.apply(n||this,arguments);};" +
                // audio noise
                "if(window.AudioBuffer){var gcd=AudioBuffer.prototype.getChannelData;" +
                "AudioBuffer.prototype.getChannelData=function(){var a=gcd.apply(this,arguments);" +
                "if(!this.__n){this.__n=1;for(var i=0;i<a.length;i+=997){a[i]+=(" + ((accountId % 9) + 1) + ")*1e-7;}}return a;};}" +
                // battery
                "var lvl=0.55+((" + accountId + "*37)%40)/100;" +
                "navigator.getBattery=function(){return Promise.resolve({charging:false,chargingTime:Infinity,dischargingTime:20000," +
                "level:lvl,addEventListener:function(){},removeEventListener:function(){}});};" +
                "}catch(e){}})();";
        return out;
    }
}
