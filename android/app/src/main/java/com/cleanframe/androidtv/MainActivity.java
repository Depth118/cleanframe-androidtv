package com.cleanframe.androidtv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Hosts the CleanFrame web UI and exposes Android TV app controls to it as window.AndroidTV. */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/index.html";
    private static final int ICON_PX = 192;

    private WebView web;
    private final Map<String, byte[]> iconCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        // Honour the page's width=device-width; otherwise the 1920px stage widens the layout
        // viewport and CleanFrame's fit-to-screen scale comes out as 1 on a 2x-density TV.
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setSupportZoom(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "AndroidTV");
        web.setWebViewClient(new AssetClient());
        setContentView(web);
        web.loadUrl(START_URL);
        web.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.resumeTimers();
    }

    @Override
    protected void onPause() {
        web.onPause();
        web.pauseTimers();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    /** The remote's Back key goes to the Activity, not the page; forward it as Escape. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) sendEscape("keydown");
            else if (event.getAction() == KeyEvent.ACTION_UP) sendEscape("keyup");
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void sendEscape(String type) {
        web.evaluateJavascript("(function(){var e=new Event('" + type + "',{bubbles:true,cancelable:true});"
                + "Object.defineProperty(e,'keyCode',{value:27});Object.defineProperty(e,'which',{value:27});"
                + "Object.defineProperty(e,'key',{value:'Escape'});document.dispatchEvent(e);})()", null);
    }

    private class AssetClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (!HOST.equals(request.getUrl().getHost())) return blocked();
            String path = request.getUrl().getPath();
            if (path == null || path.equals("/")) path = "/index.html";
            if (path.startsWith("/icon/") && path.endsWith(".png")) {
                byte[] png = icon(path.substring(6, path.length() - 4));
                return png == null ? notFound() : new WebResourceResponse("image/png", null, new ByteArrayInputStream(png));
            }
            if (path.contains("..")) return notFound();
            try {
                InputStream in = getAssets().open("home" + path);
                return new WebResourceResponse(mime(path), mime(path).startsWith("text/") || path.endsWith(".js") ? "utf-8" : null, in);
            } catch (IOException e) {
                return notFound();
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return true;
        }
    }

    private static WebResourceResponse notFound() {
        WebResourceResponse r = new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        r.setStatusCodeAndReasonPhrase(404, "Not Found");
        return r;
    }

    // The UI is fully local; nothing else is fetched.
    private static WebResourceResponse blocked() {
        WebResourceResponse r = new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        r.setStatusCodeAndReasonPhrase(403, "Forbidden");
        return r;
    }

    private static String mime(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".ttf")) return "font/ttf";
        return "text/plain";
    }

    private synchronized byte[] icon(String pkg) {
        if (iconCache.containsKey(pkg)) return iconCache.get(pkg);
        byte[] png = null;
        try {
            Drawable d = getPackageManager().getApplicationIcon(pkg);
            Bitmap bmp = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            d.setBounds(0, 0, ICON_PX, ICON_PX);
            d.draw(c);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            bmp.recycle();
            png = out.toByteArray();
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        iconCache.put(pkg, png);
        return png;
    }

    private Intent launchIntent(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i;
    }

    /** Each method returns "" on success or an error message for the page. */
    private class Bridge {
        @JavascriptInterface
        public String apps() {
            PackageManager pm = getPackageManager();
            Map<String, String> found = new LinkedHashMap<>();
            for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
                for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(getPackageName()) || found.containsKey(pkg)) continue;
                    found.put(pkg, String.valueOf(ri.loadLabel(pm)));
                }
            }
            JSONArray out = new JSONArray();
            try {
                for (Map.Entry<String, String> e : found.entrySet()) {
                    out.put(new JSONObject().put("id", e.getKey()).put("name", e.getValue()));
                }
            } catch (Exception ignored) {
            }
            return out.toString();
        }

        @JavascriptInterface
        public boolean isInstalled(String pkg) {
            try {
                getPackageManager().getPackageInfo(pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }

        @JavascriptInterface
        public String launch(String pkg) {
            Intent i = launchIntent(pkg);
            if (i == null) return "This app is not installed on the TV.";
            return start(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }

        /** Start the Android TV screensaver (Daydream / Ambient mode), the closest thing to Art Mode. */
        @JavascriptInterface
        public String screensaver() {
            Intent dream = new Intent(Intent.ACTION_MAIN)
                    .setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (start(dream).isEmpty()) return "";
            return start(new Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }

        /** Open the TV's built-in launcher (any other HOME activity). */
        @JavascriptInterface
        public String systemHome() {
            PackageManager pm = getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> homes = pm.queryIntentActivities(q, 0);
            for (ResolveInfo ri : homes) {
                ActivityInfo a = ri.activityInfo;
                if (a.packageName.equals(getPackageName()) || a.packageName.equals("com.android.settings")) continue;
                // Boot-time placeholders, not real launchers.
                if (a.packageName.equals("com.android.tv.settings") || a.packageName.equals("com.android.boot.fallbackhome")) continue;
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setComponent(new ComponentName(a.packageName, a.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (start(i).isEmpty()) return "";
            }
            return "No other Home screen is available.";
        }

        @JavascriptInterface
        public String settings() {
            return start(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }

        private String start(Intent i) {
            try {
                startActivity(i);
                return "";
            } catch (Exception e) {
                return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
        }
    }

    static {
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true);
    }
}
