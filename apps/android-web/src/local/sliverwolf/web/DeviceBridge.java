package local.sliverwolf.web;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import static local.sliverwolf.web.DshTransport.object;

/** Authenticated app boundary; standard MCP framing is owned by the Termux SDK adapter. */
final class DeviceBridge extends WebSocketServer {
    static final int PORT = 3084;
    private static final String CHANNEL = "agent-device-tools";
    private final Context context;
    private final SharedPreferences preferences;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String token;
    private final DeviceOperations operations;
    private final DeviceSpeech speech;
    private final ArtifactInbox inbox;
    final ContentReleases releases;
    private volatile boolean closed;
    private final Runnable identitySync = new Runnable() {
        public void run() {
            if(closed)return;
            shareIo.execute(()->{
                try {
                    String session=context.getSharedPreferences("session",0).getString("session","");
                    if(session.isEmpty())return;
                    String name=PresentationSettings.load(context).name;
                    DshTransport transport=new DshTransport(context.getSharedPreferences("session",0));
                    JSONObject state=transport.companion("status",null),policy=state.getJSONObject("policy");
                    if(!name.equals(policy.getString("agentName"))||!session.equals(policy.getString("sessionId"))) {
                        policy.put("agentName",name).put("sessionId",session);
                        transport.companion("policy",object("policy",policy,"expectedRevision",state.getString("revision")));
                    }
                    preferences.edit().putString("behaviorSync","已同步当前名称与会话").apply();
                }catch(Exception error){preferences.edit().putString("behaviorSync","行为同步尚未完成："+error.getClass().getSimpleName()).apply();}
            });
            main.postDelayed(this,60000);
        }
    };
    private final java.util.concurrent.ExecutorService shareIo=java.util.concurrent.Executors.newSingleThreadExecutor();
    volatile String state = "Starting";
    volatile boolean visible;
    java.util.function.BooleanSupplier microphoneActive = () -> false;

    DeviceBridge(Context context) {
        super(new InetSocketAddress("127.0.0.1", PORT), 1,
            Collections.singletonList(new Draft_6455(Collections.emptyList(), 65536)));
        this.context = context.getApplicationContext();
        preferences = context.getSharedPreferences("device-tools", Context.MODE_PRIVATE);
        String stored = preferences.getString("token", "");
        if (stored.isEmpty()) {
            byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
            stored = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
            if (!preferences.edit().putString("token", stored).commit()) throw new IllegalStateException("Cannot save pairing credential");
        }
        token = stored;
        operations = new DeviceOperations(preferences);
        try { operations.recover(); inbox = new ArtifactInbox(this.context, operations); releases=new ContentReleases(this.context);releases.recover(); }
        catch (Exception error) { throw new IllegalStateException("Device operation recovery failed", error); }
        speech = new DeviceSpeech(this.context, operations);
        speech.microphoneActive = () -> microphoneActive.getAsBoolean() || WakeVoiceService.microphoneBusy() || OwnerEnrollment.active;
        setReuseAddr(true);
        setConnectionLostTimeout(20);
        main.post(identitySync);
    }
    String pairingToken() { return token; }
    String connectionSummary(){
        long at=preferences.getLong("lastToolAt",0);
        return "本机手机工具服务："+state+"\n已认证连接："+getConnections().size()
            +"\n最近成功工具请求："+(at==0?"尚无记录":android.text.format.DateFormat.format("MM-dd HH:mm:ss",at))
            +"\n仅证明已认证客户端能访问 App；Agent 是否配置此 MCP，需在 DSH 中确认。";
    }
    @Override public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket socket,
            org.java_websocket.drafts.Draft draft, ClientHandshake request) throws InvalidDataException {
        if (!"/device-tools".equals(request.getResourceDescriptor()) || request.hasFieldValue("Origin")
                || !MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                    request.getFieldValue("Authorization").getBytes(StandardCharsets.UTF_8)))
            throw new InvalidDataException(1008, "Device tool authentication refused");
        return super.onWebsocketHandshakeReceivedAsServer(socket, draft, request);
    }
    @Override public void onStart() { state = "Available while client is open"; }
    @Override public void onOpen(WebSocket socket, ClientHandshake request) { }
    @Override public void onClose(WebSocket socket, int code, String reason, boolean remote) { }
    @Override public void onError(WebSocket socket, Exception error) { if (socket == null) state = "Bridge unavailable: " + error.getClass().getSimpleName(); }
    @Override public void onMessage(WebSocket socket, String message) {
        Runnable work=() -> {
            if (closed) return;
            Object id = JSONObject.NULL;
            try {
                JSONObject request = new JSONObject(message); id = request.get("id");
                if (!(id instanceof String) || ((String) id).length() > 80) throw new IllegalArgumentException("Invalid request ID");
                String method = request.getString("method");
                Object result;
                if ("list".equals(method)) result = tools();
                else if ("voice/pair".equals(method)) {
                    JSONObject args=request.getJSONObject("arguments");arguments(args,"token");
                    speech.pairCharacter(args.getString("token"));result=object("paired",true,"engine",CharacterSpeech.ENGINE);
                }
                else if ("voice/provider".equals(method)) {
                    if(!visible||speech.isSpeaking())throw new IllegalStateException("Open the app and finish speech before configuring a provider");
                    JSONObject args=request.getJSONObject("arguments");arguments(args,"name","url","token","model","voice");
                    VoiceProviders store=new VoiceProviders(context);
                    String provider=store.save(null,args.getString("name"),args.getString("url"),args.getString("token"));
                    result=object("provider",provider,"engine",store.select(provider,args.getString("model"),args.getString("voice")));
                }
                else if ("backend/pair".equals(method)) result = pairBackend(request.getJSONObject("arguments"));
                else if ("call".equals(method)) result = call(request.getString("name"), request.getJSONObject("arguments"));
                else if (method.startsWith("share/")) result = new ShareInbox(context).transfer(method,request.getJSONObject("arguments"));
                else if (method.startsWith("artifact/")) result = inbox.request(method, request.getJSONObject("arguments"));
                else throw new IllegalArgumentException("Unknown bridge method");
                if("call".equals(method))preferences.edit().putLong("lastToolAt",System.currentTimeMillis()).apply();
                if (result instanceof JSONObject) ((JSONObject) result).remove("inputDigest");
                if (socket.isOpen()) socket.send(object("id", id, "result", result).toString());
            } catch (Exception error) {
                if (socket.isOpen()) socket.send(object("id", id, "error", error.getMessage() == null ? "Device operation failed" : error.getMessage()).toString());
            }
        };
        try {if(new JSONObject(message).optString("method").startsWith("share/")||new JSONObject(message).optString("method").startsWith("artifact/")||new JSONObject(message).optString("name").startsWith("assistant_native_")||new JSONObject(message).optString("name").equals("assistant_stage_apk")||new JSONObject(message).optString("name").equals("assistant_install_apk"))shareIo.execute(work);else main.post(work);}
        catch(Exception error){main.post(work);}
    }
    private JSONObject pairBackend(JSONObject args) throws Exception {
        arguments(args, "protocol", "port", "cookie");
        if (!"0.2.1-rc.3".equals(args.getString("protocol"))) throw new IllegalArgumentException("Unsupported backend protocol");
        int port = args.getInt("port");
        if (port != 3080) throw new IllegalArgumentException("Only the relay entrance (3080) may be paired");
        String cookie = args.getString("cookie");
        if (cookie.length() < 16 || cookie.length() > 4096 || !cookie.matches("[A-Za-z0-9_=-]+=[A-Za-z0-9_.=-]+")) throw new IllegalArgumentException("Invalid backend credential");
        SharedPreferences session = context.getSharedPreferences("session", 0);
        if (!session.edit().putString("backendProtocol", "0.2.1-rc.3").putInt("backendPort", port)
                .putString("backendCookie", cookie).putLong("backendRevision", System.currentTimeMillis()).commit())
            throw new IllegalStateException("Cannot save backend pairing");
        return object("paired", true, "protocol", "0.2.1-rc.3", "port", port);
    }
    private JSONArray tools() {
        JSONObject empty = object("type", "object", "properties", new JSONObject(), "additionalProperties", false);
        JSONObject key = object("type", "string", "minLength", 1, "maxLength", 80);
        return new JSONArray()
            .put(object("name","assistant_native_status","description","Read local APK release state and installed version.","inputSchema",empty))
            .put(object("name","assistant_stage_apk","description","Stage a completed native-apk artifact. Receipt requires releaseId, baseCode, targetCode, apkSha256, sourceSha256 and dexSha256. No installation occurs.","inputSchema",object("type","object","properties",object("operationKey",key,"receipt",object("type","object")),"required",new JSONArray().put("operationKey").put("receipt"),"additionalProperties",false)))
            .put(object("name","assistant_install_apk","description","Request system-confirmed in-place installation of the staged APK. Requires foreground idle client and installation permission; never silently installs.","inputSchema",empty))
            .put(object("name","assistant_release_status","description","Read local release receipts, or exact current payload/version for componentId presentation or profile.","inputSchema",object("type","object","properties",object("componentId",object("type","string","enum",new JSONArray().put("presentation").put("profile"))),"additionalProperties",false)))
            .put(object("name","assistant_prepare_profile","description","Normalize profile parameters into the exact Android payload and digest without changing active state. appearanceVersion is empty for built-in; voice is the six-field voice-profile config from status.","inputSchema",object("type","object","properties",object("displayName",key,"appearanceVersion",object("type","string"),"voice",object("type","object")),"required",new JSONArray().put("displayName").put("appearanceVersion").put("voice"),"additionalProperties",false)))
            .put(object("name","assistant_import_appearance","description","Import a completed authenticated artifact as an inert Codex v2 appearance. Does not activate. Use its version in a profile release.","inputSchema",object("type","object","properties",object("operationKey",key),"required",new JSONArray().put("operationKey"),"additionalProperties",false)))
            .put(object("name","assistant_stage_release","description","Stage a user-requested content release through this authenticated local channel. Does not activate. Requires schemaVersion, releaseId, componentId=presentation or profile, exact baseVersion/targetVersion SHA-256, payload string, minClientCode, activationMode=reload and verificationNote. The note is a caller claim, not a passing test.","inputSchema",object("type","object","properties",object("manifest",object("type","object")),"required",new JSONArray().put("manifest"),"additionalProperties",false)))
            .put(object("name","assistant_release_action","description","Explicitly activate, roll back or discard a local content release. Activation/rollback require the foreground client with idle audio; they never restart DSH or modify native code. Poll status after uncertain delivery.","inputSchema",object("type","object","properties",object("releaseId",key,"action",object("type","string","enum",new JSONArray().put("activate").put("rollback").put("discard"))),"required",new JSONArray().put("releaseId").put("action"),"additionalProperties",false)))
            .put(object("name","assistant_receive_share","description","Receive only a user-authorized Android share into private Termux files. Requires the original share and session IDs. Retrying this file transfer does not submit another prompt.",
                "inputSchema",object("type","object","properties",object("shareId",object("type","string","maxLength",36),"sessionId",object("type","string","maxLength",200)),"required",new JSONArray().put("shareId").put("sessionId"),"additionalProperties",false)))
            .put(object("name", "assistant_get_status", "description", "Read actual Android client, speech engine, and notification availability. Voice is manually controlled or explicitly enabled for background wake; overlay permission is separate.", "inputSchema", empty))
            .put(object("name", "assistant_show", "description", "Post a notification that the user can tap to open the assistant. Does not force background UI launch. Reuse operationKey for retries within 24 hours.",
                "inputSchema", object("type", "object", "properties", object("surface", object("type", "string", "enum", new JSONArray().put("notification")), "operationKey", key), "required", new JSONArray().put("surface").put("operationKey"), "additionalProperties", false)))
            .put(object("name", "assistant_get_operation", "description", "Read a retained device operation without repeating it.", "inputSchema", object("type", "object", "properties", object("operationKey", key), "required", new JSONArray().put("operationKey"), "additionalProperties", false)))
            .put(object("name", "assistant_speak", "description", "Speak explicit text using installed Android TTS. Acceptance is not completion; poll assistant_get_operation. Reuse operationKey for retries; never auto-read ordinary chat replies.", "inputSchema", object("type", "object", "properties", object("operationKey", key, "text", object("type", "string", "minLength", 1, "maxLength", 1000), "language", object("type", "string", "enum", new JSONArray().put("zh-CN").put("en-US"))), "required", new JSONArray().put("operationKey").put("text"), "additionalProperties", false)))
            .put(object("name", "assistant_stop_speaking", "description", "Stop current explicit playback; optional operationId restricts the target. Does not require a model response or microphone access.", "inputSchema", object("type", "object", "properties", object("operationKey", key, "operationId", object("type", "string", "maxLength", 80)), "required", new JSONArray().put("operationKey"), "additionalProperties", false)));
    }
    private JSONObject call(String name, JSONObject args) throws Exception {
        if(name.startsWith("assistant_screen_")){
            if("assistant_screen_status".equals(name))return object("enabled",ScreenControlService.enabled());
            if("assistant_screen_dump".equals(name))return ScreenControlService.dump(120);
            if("assistant_screen_click".equals(name)){arguments(args,"text");return ScreenControlService.clickText(args.getString("text"));}
            if("assistant_screen_tap".equals(name)){arguments(args,"x","y");return ScreenControlService.tap((float)args.getDouble("x"),(float)args.getDouble("y"));}
            if("assistant_screen_type".equals(name)){arguments(args,"text");return ScreenControlService.type(args.getString("text"));}
            if("assistant_screen_global".equals(name)){arguments(args,"action");return ScreenControlService.global(args.getString("action"));}
            throw new IllegalArgumentException("Unknown screen tool");
        }
        if("assistant_native_status".equals(name)){arguments(args);return new NativeReleases(context).status();}
        if("assistant_stage_apk".equals(name)){arguments(args,"operationKey","receipt");return new NativeReleases(context).stage(inbox.verified(args.getString("operationKey")),args.getJSONObject("receipt"));}
        if("assistant_install_apk".equals(name)){arguments(args);return new NativeReleases(context).install(visible&&!speech.isSpeaking()&&!microphoneActive.getAsBoolean()&&!WakeVoiceService.microphoneBusy()&&!releases.unsettled());}
        if("assistant_prepare_profile".equals(name)){
            arguments(args,"displayName","appearanceVersion","voice");
            AssistantProfile profile=new AssistantProfile(args.getString("displayName"),args.getString("appearanceVersion"),AssistantProfile.Voice.parse(args.getJSONObject("voice")));
            String payload=profile.json().toString();return object("payload",payload,"targetVersion",PresentationSettings.digest(payload));
        }
        if("assistant_release_status".equals(name)){arguments(args,"componentId");releases.baselineVoice(speech.voiceProfile());return args.has("componentId")?releases.component(args.getString("componentId")):releases.snapshot();}
        if("assistant_import_appearance".equals(name)){
            arguments(args,"operationKey");PetStore store=new PetStore(new java.io.File(context.getFilesDir(),"appearances"));
            try(java.io.FileInputStream input=new java.io.FileInputStream(inbox.verified(args.getString("operationKey")))){
                PetStore.Pack pack=store.importZip(input);try{return object("appearanceVersion",pack.key,"displayName",pack.name,"activated",false);}finally{pack.sprite.bitmap.recycle();}
            }
        }
        if("assistant_stage_release".equals(name)){arguments(args,"manifest");return releases.stage(args.getJSONObject("manifest"));}
        if("assistant_release_action".equals(name)){
            arguments(args,"releaseId","action");String id=args.getString("releaseId"),action=args.getString("action");
            boolean safe=visible&&!speech.isSpeaking()&&!microphoneActive.getAsBoolean()&&!WakeVoiceService.microphoneBusy()&&!OwnerEnrollment.active;
            if("activate".equals(action))return releases.activate(id,safe);
            if("rollback".equals(action))return releases.rollback(id,safe);
            if("discard".equals(action))return releases.discard(id);
            throw new IllegalArgumentException("Unknown release action");
        }
        if ("assistant_get_status".equals(name)) {
            if (args.length() != 0) throw new IllegalArgumentException("Status takes no arguments");
            return object("appVersion", context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName, "uiVisible", visible,
                "notificationsEnabled", context.getSystemService(NotificationManager.class).areNotificationsEnabled(),
                "voice", speech.status(), "microphone", "foreground-user-controlled", "microphoneActive", microphoneActive.getAsBoolean(),
                "wake", WakeVoiceService.diagnostics(), "overlay", context.getSharedPreferences("overlay",0).getBoolean("enabled",false)?"enabled":"disabled", "artifactTransfer", "authenticated-chunks");
        }
        if ("assistant_speak".equals(name)) return speech.speak(args);
        if ("assistant_stop_speaking".equals(name)) return speech.stop(args);
        String key = args.getString("operationKey");
        DeviceOperations.validateKey(key);
        if ("assistant_get_operation".equals(name)) {
            if (args.length() != 1) throw new IllegalArgumentException("Unexpected operation arguments");
            return operations.get(key);
        }
        if (!"assistant_show".equals(name)) throw new IllegalArgumentException("Unsupported tool");
        if (args.length() != 2 || !"notification".equals(args.optString("surface"))) throw new IllegalArgumentException("Only notification surface is supported");
        String fingerprint = DeviceOperations.digest("notification");
        JSONObject previous = operations.previous(key, "show", fingerprint);
        if (previous != null) return previous;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Agent device tools", NotificationManager.IMPORTANCE_DEFAULT));
        if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL).getImportance() == NotificationManager.IMPORTANCE_NONE)
            throw new IllegalStateException("Notification permission required");
        JSONObject result = operations.reserve(key, "show", fingerprint);
        try {
            PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            manager.notify("device-tool", 1, new Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("LumenDSH")
                .setContentText("Tap to open your assistant").setContentIntent(open).setAutoCancel(true).build());
            result.put("state", "succeeded").put("detail", "Notification posted; user tap is required to open UI");
        } catch (RuntimeException error) { result.put("state", "failed").put("detail", "Android refused notification"); }
        operations.update(key, result);
        return result;
    }
    static void arguments(JSONObject args, String... allowed) {
        java.util.Set<String> names = new java.util.HashSet<>(java.util.Arrays.asList(allowed));
        java.util.Iterator<String> keys = args.keys();
        while (keys.hasNext()) if (!names.contains(keys.next())) throw new IllegalArgumentException("invalid-input: unexpected argument");
    }
    String speechNotice(){return speech.notice();}
    boolean synthesizing(){return speech.synthesizing();}
    String spokenText() {return speech.spokenText();}
    boolean profileReady() {return speech.profileReady();}
    void holdProfile(boolean hold) {speech.holdProfile(hold);}
    boolean characterConfigured(){return speech.characterConfigured();}
    String systemVoiceEngine(){return speech.systemEngine();}
    AssistantProfile.Voice voiceProfile() {return speech.voiceProfile();}
    void beginProfile(AssistantProfile.Voice voice) {speech.beginProfile(voice);}
    void finishProfile(AssistantProfile.Voice voice) {speech.finishProfile(voice);}
    boolean isSpeaking() { return speech.isSpeaking(); }
    void stopSpeaking() { speech.stopLocal("Stopped from client control"); }
    void speakReply(String key, String text) throws Exception {
        speech.speak(object("operationKey", key, "text", text, "language", speech.voiceProfile().language));
    }
    void closeDevices() { closed = true; main.removeCallbacks(identitySync); shareIo.shutdownNow(); speech.close(); }
}
