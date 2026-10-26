package local.sliverwolf.web;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Keeps a tiny headless WebView on the official UI so the tide plugin's own
 * client state (with holiday handling) is available on the native home without
 * the user opening the chat first. Polls every 10 s and forwards through
 * PeriodBridge.
 */
final class PeriodWatcher {
    private static final String POLL = "(function(){function t(){try{var s=window.__liangwenTide&&window.__liangwenTide.state&&window.__liangwenTide.state();if(s&&s.phase)SliverWolfNative.report(JSON.stringify({phase:s.phase,flipAt:s.flipAt||0}));}catch(e){}}t();setInterval(t,10000);})()";
    private static WebView view;

    private PeriodWatcher() { }

    static void start(Activity activity, ViewGroup container, SharedPreferences session) {
        if (view != null) return;
        String cookie = session.getString("backendCookie", "");
        if (cookie.isEmpty()) { ConnectionBootstrap.run(activity); return; }
        WebView web = new WebView(activity);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        web.addJavascriptInterface(new PeriodBridge(activity), "SliverWolfNative");
        String origin = "http://127.0.0.1:" + session.getInt("backendPort", 3080);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) { v.evaluateJavascript(POLL, null); }
        });
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(1, 1);
        web.setAlpha(0.01f);
        container.addView(web, params);
        CookieManager.getInstance().setCookie(origin, cookie, value -> {
            CookieManager.getInstance().flush();
            web.loadUrl(origin + "/");
        });
        view = web;
    }

    static void stop() {
        if (view == null) return;
        android.view.ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(view);
        view.destroy();
        view = null;
    }
}
