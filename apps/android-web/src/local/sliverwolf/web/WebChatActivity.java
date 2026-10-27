package local.sliverwolf.web;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.*;
import android.speech.*;
import org.json.JSONObject;
import java.util.ArrayList;
import android.widget.*;

/**
 * The M10 chat surface: the official DSH Web UI in a WebView, authenticated with
 * the entrance cookie fetched from Termux. Native screens remain available for
 * pairing and as a fallback.
 */
public final class WebChatActivity extends Activity {
    private WebView web;
    private TextView status;
    private String pendingShare = "";
    private SpeechRecognizer recognizer;
    private SharedPreferences prefs;
    private final BroadcastReceiver ready = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String error = intent.getStringExtra("error");
            if (error != null) { showFallback(error); return; }
            load();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        CrashLog.install(this);
        CrashLog.mark(this, "webchat:create");
        try { build(state); } catch (Throwable error) {
            CrashLog.mark(this, "webchat:build-error " + error);
            TextView view = new TextView(this);
            view.setTextSize(11);
            view.setTextColor(Color.WHITE);
            view.setBackgroundColor(Color.rgb(30, 10, 10));
            view.setText("官方界面启动失败：\n" + android.util.Log.getStackTraceString(error));
            setContentView(view);
        }
    }

    private void build(Bundle state) {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 44);
        }
        android.content.Intent startIntent = getIntent();
        if (startIntent != null && android.content.Intent.ACTION_SEND.equals(startIntent.getAction())) {
            String text = startIntent.getStringExtra(android.content.Intent.EXTRA_TEXT);
            if (text != null) pendingShare = text;
        }
        CrashLog.mark(this, "webchat:build");
        prefs = ConnectionBootstrap.session(this);
        if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"com.termux.permission.RUN_COMMAND"}, 42);
        }

        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setTextColor(Color.WHITE);
        status.setBackgroundColor(Color.rgb(16, 18, 24));
        status.setTextSize(16);
        status.setVisibility(android.view.View.GONE);
        root.addView(web, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(status, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        CrashLog.mark(this, "webchat:view");

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        web.addJavascriptInterface(new PeriodBridge(this), "SliverWolfNative");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                CrashLog.mark(WebChatActivity.this, "console:" + message.message() + " @" + message.lineNumber());
                return true;
            }
            @Override public void onPermissionRequest(final PermissionRequest request) {
                boolean audio = false;
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) audio = true;
                }
                if (audio && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else {
                    CrashLog.mark(WebChatActivity.this, "mic:denied");
                    request.deny();
                }
            }
        });
        web.addJavascriptInterface(new ShellBridge(), "LumenShell");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if (host != null && (host.equals("127.0.0.1") || host.equals("localhost") || host.equals("127.0.0.2"))) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                CrashLog.mark(WebChatActivity.this, "page:" + url);
                if (!pendingShare.isEmpty()) { final String share = pendingShare; pendingShare = ""; postComposer(share); }
                view.evaluateJavascript("document.title+'|'+location.href+'|'+(document.body?document.body.innerText.slice(0,160):'nobody')", value -> CrashLog.mark(WebChatActivity.this, "dom:" + value));
                view.evaluateJavascript("(function(){function t(){try{var s=window.__liangwenTide&&window.__liangwenTide.state&&window.__liangwenTide.state();if(s&&s.phase)SliverWolfNative.report(JSON.stringify({phase:s.phase,flipAt:s.flipAt||0}));}catch(e){}}t();setInterval(t,10000);})()", null);
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                CrashLog.mark(WebChatActivity.this, "http:" + response.getStatusCode() + " " + request.getUrl());
                if (request.isForMainFrame() && response.getStatusCode() == 401) {
                    prefs.edit().remove("backendCookie").commit();
                    showFallback("认证失效，重新获取连接…");
                    ConnectionBootstrap.run(WebChatActivity.this);
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showFallback("无法连接 DSH 入口：" + error.getDescription());
            }
        });

        if (state != null) web.restoreState(state);
        if (prefs.getString("backendCookie", "").isEmpty()) beginBootstrap(); else load();
    }

    private void beginBootstrap() {
        showStatus("正在从 Termux 获取 DSH 连接…");
        ConnectionBootstrap.run(this);
        final android.os.Handler handler = new android.os.Handler(getMainLooper());
        final long deadline = System.currentTimeMillis() + 20000;
        handler.post(new Runnable() {
            @Override public void run() {
                if (!prefs.getString("backendCookie", "").isEmpty()) { load(); return; }
                if (System.currentTimeMillis() > deadline) { showFallback("未能从 Termux 取得 DSH 连接"); return; }
                handler.postDelayed(this, 500);
            }
        });
    }

    private void showStatus(String text) {
        status.setText(text);
        status.setVisibility(android.view.View.VISIBLE);
        web.setVisibility(android.view.View.GONE);
    }

    private void showFallback(String message) {
        showStatus(message + "\n\n点此打开原生界面");
        status.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
    }

    /** Native actions exposed to the official page's shell plugin. */
    public final class ShellBridge {
        @android.webkit.JavascriptInterface public void home() { runOnUiThread(() -> finish()); }
        @android.webkit.JavascriptInterface public void voice() { runOnUiThread(WebChatActivity.this::startVoice); }
        @android.webkit.JavascriptInterface public String capability() { return "shell/1"; }
    }

    private void startVoice() {
        try {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 43);
                return;
            }
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                recognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onResults(android.os.Bundle results) {
                        ArrayList<String> values = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (values != null && !values.isEmpty()) forwardVoice(values.get(0));
                    }
                    @Override public void onError(int error) { }
                    @Override public void onReadyForSpeech(android.os.Bundle params) { }
                    @Override public void onBeginningOfSpeech() { }
                    @Override public void onRmsChanged(float rmsdB) { }
                    @Override public void onBufferReceived(byte[] buffer) { }
                    @Override public void onEndOfSpeech() { }
                    @Override public void onPartialResults(android.os.Bundle partialResults) { }
                    @Override public void onEvent(int eventType, android.os.Bundle params) { }
                });
            }
            android.content.Intent intent = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
            recognizer.startListening(intent);
        } catch (Throwable ignored) { }
    }

    private void forwardVoice(String text) {
        if (text == null || text.trim().isEmpty()) return;
        postComposer(text.trim());
    }

    private void postComposer(String text) {
        if (text == null || text.isEmpty()) return;
        new Thread(() -> {
            try {
                android.content.SharedPreferences session = getSharedPreferences("session", 0);
                String cookie = session.getString("backendCookie", "");
                int port = session.getInt("backendPort", 3080);
                if (cookie.isEmpty()) { CrashLog.mark(this, "composer:no-cookie"); return; }
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(
                    "http://127.0.0.1:" + port + "/sliver-wolf/companion/composer").openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Cookie", cookie);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setConnectTimeout(4000); connection.setReadTimeout(6000);
                connection.setDoOutput(true);
                byte[] body = new org.json.JSONObject().put("text", text).toString().getBytes("UTF-8");
                try (java.io.OutputStream out = connection.getOutputStream()) { out.write(body); }
                int status = connection.getResponseCode();
                CrashLog.mark(this, "composer:post:" + status);
                connection.disconnect();
            } catch (Exception error) { CrashLog.mark(this, "composer:error:" + error.getMessage()); }
        }).start();
    }

    private void injectText(String text) {
        if (text == null || text.isEmpty() || web == null) return;
        String quoted;
        try { quoted = JSONObject.quote(text); } catch (Exception error) { return; }
        String script = "(function(){var text=" + quoted + ";var tries=0;function set(){tries++;" +
            "var el=document.querySelector('textarea')||document.querySelector('[contenteditable=true]')||document.querySelector('[role=textbox]');" +
            "if(!el){if(tries<40)setTimeout(set,500);return;}" +
            "try{if(el.tagName==='TEXTAREA'){var s=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set;s.call(el,text);}" +
            "else{el.focus();el.textContent=text;}}" +
            "el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));" +
            "if(tries<40&&(!el.textContent||el.textContent.indexOf(text)<0))setTimeout(set,500);}set();})()";
        web.evaluateJavascript(script, null);
    }

    private void load() {
        int port = prefs.getInt("backendPort", 3080);
        String cookie = prefs.getString("backendCookie", "");
        if (cookie.isEmpty()) { showFallback("尚未取得 DSH 连接"); return; }
        String origin = "http://127.0.0.1:" + port;
        status.setVisibility(android.view.View.GONE);
        web.setVisibility(android.view.View.VISIBLE);
        CookieManager.getInstance().setCookie(origin, cookie, value -> {
            CrashLog.mark(this, "webchat:cookie");
            String current = CookieManager.getInstance().getCookie(origin);
            CrashLog.mark(this, "cookie:" + (current == null ? "null" : "len=" + current.length()));
            CookieManager.getInstance().flush();
            web.loadUrl(origin + "/");
        });
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 42 && results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            beginBootstrap();
        }
    }

    @Override protected void onResume() { super.onResume(); registerReceiver(ready, new IntentFilter(ConnectionBootstrap.ACTION_READY)); }
    @Override protected void onPause() { try { unregisterReceiver(ready); } catch (Exception ignored) { } super.onPause(); }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
