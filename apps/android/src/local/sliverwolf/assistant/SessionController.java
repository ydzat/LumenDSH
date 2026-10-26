package local.sliverwolf.assistant;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.java_websocket.client.WebSocketClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static local.sliverwolf.assistant.DshTransport.object;

/** All mutable state belongs to the main thread; generation fences discard obsolete I/O. */
final class SessionController {
    interface Listener { void changed(); }
    interface Job { JSONObject run() throws Exception; }
    interface Result { void accept(JSONObject value) throws Exception; }
    final Conversation conversation = new Conversation();
    final Map<String, JSONObject> decisions = new LinkedHashMap<>();
    JSONArray sessions = new JSONArray();
    JSONObject modelInfo;
    String modelError="尚未连接";
    private boolean modelBusy,modelAgain;
    void refreshModel() {
        if(!connected||selected.isEmpty())return;
        if(modelBusy){modelAgain=true;return;}
        modelBusy=true; modelInfo=null; modelError="正在读取 DSH 模型配置";
        final int epoch=generation;final String id=selected;
        io.execute(()->{
            JSONObject result=null;String error="";
            try{result=wire.model(id);}catch(Exception e){error="模型配置不可用 · "+e.getClass().getSimpleName();}
            final JSONObject value=result;final String failure=error;
            main.post(()->{if(!current(epoch)||!id.equals(selected))return;
                modelBusy=false;modelInfo=value;modelError=failure;changed();
                if(modelAgain){modelAgain=false;refreshModel();}
            });
        });
    }
    String selected, connection = "Disconnected", notice = "", pendingId, pendingSession, pendingState;
    boolean connected, running, historyBusy, creating;
    private final SharedPreferences preferences;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final DshTransport wire;
    private final SharedPreferences.OnSharedPreferenceChangeListener backendChanges = (prefs, key) -> {
        if ("backendRevision".equals(key)) main.post(this::backendChanged);
    };
    private WebSocketClient mux, host;
    private int generation, opened, failures, hostRevision;
    private long questionSeq = -1;
    private boolean stopped = true, destroyed, describeReady, listBusy;
    private long retryAt, lastConnectedAt, checkedAt;
    private String lastFailure="", checkCode="NOT_CHECKED";
    private boolean checking, hostReady, sessionReady;
    private final Runnable handshakeDeadline=()->lost(generation,"STREAM_TIMEOUT");
    private final Runnable reconnect = () -> connect();
    void diagnose() {
        if(checking||destroyed)return;
        checking=true;final int epoch=generation;
        final DshTransport probe=new DshTransport(preferences);
        io.execute(()->{String code="OK";
            try{probe.call("session.list",object()).getJSONArray("items");}catch(Exception e){code=DshTransport.failureCode(e);}
            final String result=code;main.post(()->{checking=false;if(destroyed)return;
                if(epoch==generation){checkCode=result;checkedAt=System.currentTimeMillis();}changed();});
        });changed();
    }
    private String time(long value){return value==0?"尚无记录":android.text.format.DateFormat.format("MM-dd HH:mm:ss",value).toString();}
    private String advice(String code) {
        if(code.equals("SERVICE_UNREACHABLE"))return "本机 DSH 端口不可达。点击启动 DSH；若反复退出，检查 Termux 的后台运行设置。端口不可达本身不能判断退出原因。";
        if(code.equals("HTTP_401")||code.equals("HTTP_403"))return "DSH 有响应，但身份验证失败。检查后端配对；不要创建新会话来解决认证问题。";
        if(code.equals("TIMEOUT"))return "DSH 响应超时。检查 Termux 是否仍在运行、设备负载及后台限制，再重新检测。";
        if(code.equals("RPC_REFUSED"))return "DSH 拒绝接口请求。检查所选会话和后端版本；不会自动新建会话。";
        if(code.startsWith("WS_")||code.startsWith("STREAM_"))return "事件通道中断或订阅未就绪。先检测服务与认证，再重新连接原会话。";
        if(code.equals("OK"))return "服务与认证检测通过。事件订阅和任务状态见上方；LLM 与语音服务分别检测。";
        if(code.equals("NOT_CHECKED"))return "点击检测，检查本机服务及认证，不发送消息。";
        return "服务接口或协议异常（"+code+"）。检查 DSH 版本与端口，再重新检测。";
    }
    String diagnostics() {
        String code=checkCode;
        String state=connected?"已连接":stopped?"已手动断开":"正在连接 / 重试";
        String result="App → DSH："+state+"\n地址：http://127.0.0.1:"+preferences.getInt("backendPort",3080)
            +"\n服务与认证："+(checking?"检测中":code)+" · "+time(checkedAt)
            +"\n主机事件："+(hostReady?"已订阅":"未就绪")+"\n会话事件："+(sessionReady?(selected.isEmpty()?"尚未选择会话":"已同步"):"未就绪")
            +"\n任务："+taskState()+"\n最近连接成功："+time(lastConnectedAt);
        if(!stopped&&!connected&&retryAt>0)result+="\n下次重试：约 "+Math.max(0,(retryAt-android.os.SystemClock.elapsedRealtime()+999)/1000)+" 秒";
        if(!lastFailure.isEmpty())result+="\n最近故障："+lastFailure+" · "+time(preferences.getLong("connectionFailureAt",0));
        result+="\n\n"+advice(code.equals("NOT_CHECKED")&&!lastFailure.isEmpty()?lastFailure:code);
        if(!pendingId.isEmpty())result+="\n有待确认的发送记录；恢复后核对历史，不自动重发。";
        return result;
    }
    private void backendChanged() { if (!stopped && !destroyed) connect(); }

    SessionController(SharedPreferences preferences, Listener listener) {
        this.preferences = preferences;
        wire = new DshTransport(preferences);
        preferences.registerOnSharedPreferenceChangeListener(backendChanges);
        this.listener = listener;
        lastFailure=preferences.getString("connectionFailure","");lastConnectedAt=preferences.getLong("connectionSuccessAt",0);
        selected = preferences.getString("session", "");
        pendingId = preferences.getString("pendingId", "");
        pendingSession = preferences.getString("pendingSession", "");
        pendingState = pendingId.isEmpty() ? "" : "delivery-unknown";
    }
    void connect() {
        if (destroyed) return;
        stopped = false;
        closeGeneration();
        wire.refresh();
        final int epoch = generation;
        connection = "正在连接本机 DSH";retryAt=0;
        decisions.clear();
        conversation.clear();
        questionSeq = -1;
        running = false;
        changed();
        request(() -> wire.call("host.describe", object()), value -> {
            describeReady=true;checkCode="OK";checkedAt=System.currentTimeMillis();
            mux=socket("events.mux",epoch);host=socket("events.host",epoch);
            main.postDelayed(handshakeDeadline,20000);
        }, epoch);
    }
    void disconnect() {
        stopped = true;
        closeGeneration();
        connection = "客户端已手动断开 · 不停止 DSH";retryAt=0;
        decisions.clear();
        changed();
    }
    private void closeGeneration() {
        generation++;
        modelInfo=null;modelError="未连接 · 不使用缓存模型配置";modelBusy=false;modelAgain=false;
        main.removeCallbacks(reconnect);main.removeCallbacks(handshakeDeadline);
        connected = false;hostReady=false;sessionReady=false; opened = 0; describeReady = false; historyBusy = false; listBusy = false; creating = false;
        if (mux != null) mux.close();
        if (host != null) host.close();
        mux = null; host = null;
    }
    private WebSocketClient socket(String channel, int epoch) {
        return wire.stream(channel, selected, new DshTransport.Events() {
            public void opened() { main.post(() -> { if (current(epoch)) { opened++;if("events.host".equals(channel))hostReady=true;else sessionReady=true;ready();changed(); } }); }
            public void frame(JSONObject frame) { main.post(() -> {
                if (!current(epoch)) return;
                try { receive(frame); } catch (Exception error) { SessionController.this.lost(epoch, "STREAM_PROTOCOL"); }
            }); }
            public void lost() { lost("STREAM_LOST"); }
            public void lost(String reason){main.post(()->SessionController.this.lost(epoch,reason));}
        });
    }
    private void ready() {
        if (connected || opened != 2 || !describeReady) return;
        connected = true; failures = 0;retryAt=0;main.removeCallbacks(handshakeDeadline);
        connection = "已连接 · 本机 DSH · 已认证";lastConnectedAt=System.currentTimeMillis();
        preferences.edit().putLong("connectionSuccessAt",lastConnectedAt).apply();
        refreshSessions();
        refreshModel();
        changed();
    }
    private void lost(int epoch, String message) {
        if (!current(epoch)) return;
        closeGeneration();
        decisions.clear();
        if (!pendingId.isEmpty()) pendingState = "delivery-unknown";
        lastFailure=message;preferences.edit().putString("connectionFailure",message).putLong("connectionFailureAt",System.currentTimeMillis()).apply();
        if(!message.startsWith("WS_")&&!message.startsWith("STREAM_")){checkCode=message;checkedAt=System.currentTimeMillis();}
        connection = "连接中断 · "+message+" · 自动重连";
        long delay=Math.min(30000,1000L << Math.min(failures++,5));retryAt=android.os.SystemClock.elapsedRealtime()+delay;
        main.postDelayed(reconnect,delay);
        changed();
    }
    private boolean current(int epoch) { return !stopped && !destroyed && epoch == generation; }
    private void changed() { if (!destroyed) listener.changed(); }
    private void request(Job job, Result success, int epoch) {
        io.execute(() -> {
            try {
                JSONObject result = job.run();
                main.post(() -> {
                    if (!current(epoch)) return;
                    try { success.accept(result); } catch (Exception error) { notice = "Invalid server response: " + error.getClass().getSimpleName(); }
                    changed();
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (!current(epoch)) return;
                    historyBusy = false; listBusy = false; creating = false;
                    if (describeReady && error instanceof DshTransport.Refused) { notice = "Request refused: " + error.getMessage(); changed(); }
                    else lost(epoch, DshTransport.failureCode(error));
                });
            }
        });
    }
    void select(String id) {
        if (id.equals(selected)) return;
        if (!preferences.edit().putString("session", id).commit()) { notice = "Cannot save selection"; changed(); return; }
        selected = id; notice = "";
        connect(); // New subscriptions replay the selected session's pending decisions.
    }
    void create() {
        if (!connected || creating) return;
        creating = true; notice = "Creating an explicit new conversation…"; changed();
        final int epoch = generation;
        // Creation is a user gesture and is never replayed after an uncertain response.
        request(() -> wire.call("session.create", object()), value -> {
            creating = false;
            select(value.getString("sessionId"));
        }, epoch);
    }
    void refreshSessions() {
        if (!connected || listBusy) return;
        listBusy = true;
        int revision = hostRevision;
        request(() -> wire.call("session.list", object()), value -> {
            listBusy = false;
            sessions = value.getJSONArray("items");
            if (revision == hostRevision) {
                for (int i = 0; i < sessions.length(); i++) {
                    JSONObject item = sessions.getJSONObject(i);
                    if (selected.equals(item.getString("sessionId"))) running = item.getBoolean("running");
                }
            }
        }, generation);
    }
    void loadHistory(boolean older) {
        // The new follow stream owns the current window, including queued prompt reconciliation.
        if (!older) return;
        if (!connected || selected.isEmpty() || historyBusy) return;
        historyBusy = true;
        JSONObject payload = object("sessionId", selected, "maxMessages", 50);
        if (older && conversation.oldest != Long.MAX_VALUE) payload = object("sessionId", selected, "maxMessages", 50, "beforeSeq", conversation.oldest);
        final JSONObject parameters = payload;
        request(() -> wire.call("session.history", parameters), value -> {
            historyBusy = false;
            JSONArray entries = value.getJSONArray("events");
            for (int i = 0; i < entries.length(); i++) conversation.add(entries.getJSONObject(i).getJSONObject("event"));
            conversation.hasMore = value.getBoolean("hasMore");
            reconcilePending();
        }, generation);
    }
    private void syncPending() {
        String stored=preferences.getString("pendingId", "");
        if(!stored.equals(pendingId)) {
            pendingId=stored;pendingSession=preferences.getString("pendingSession", "");
            pendingState=stored.isEmpty()?"":"delivery-unknown";
        }
    }
    private void reconcilePending() {
        syncPending();
        if (selected.equals(pendingSession) && !pendingId.isEmpty() && conversation.containsRequest(pendingId)) clearPending();
    }
    private void clearPending() {
        if(!pendingId.equals(preferences.getString("pendingId", ""))){syncPending();return;}
        if (preferences.edit().remove("pendingId").remove("pendingSession").commit()) {
            pendingId = ""; pendingSession = ""; pendingState = "";
        } else notice = "Cannot persist delivery confirmation";
    }
    boolean canSend() { syncPending(); return connected && !selected.isEmpty() && pendingId.isEmpty() && !historyBusy; }
    boolean send(String text) {
        return send(text,UUID.randomUUID().toString());
    }
    boolean send(String text,String requestId) {
        if (!canSend() || text.trim().isEmpty()) return false;
        final String id = UUID.fromString(requestId).toString();
        final String session = selected;
        if (!preferences.edit().putString("pendingId", id).putString("pendingSession", session).commit()) {
            notice = "Cannot save request; nothing submitted"; changed(); return false;
        }
        pendingId = id; pendingSession = session; pendingState = "submitting";
        notice = ""; changed();
        final int epoch = generation;
        io.execute(() -> {
            try {
                JSONObject reply = wire.call("session.prompt", object("sessionId", session, "mode", "queue",
                    "content", new JSONArray().put(object("type", "text", "text", text))), id);
                if (!reply.getBoolean("accepted")) throw new java.io.IOException("Missing acceptance");
                main.post(() -> {
                    if (destroyed || !id.equals(pendingId)) return;
                    pendingState = "accepted · awaiting history";
                    if (current(epoch)) loadHistory(false);
                    changed();
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (destroyed || !id.equals(pendingId)) return;
                    if (error instanceof DshTransport.Refused) {
                        clearPending(); notice = "Prompt refused: " + error.getMessage();
                    } else pendingState = "delivery-unknown · not retried";
                    changed();
                });
            }
        });
        return true;
    }
    void acknowledgeUnknown() {
        if (!pendingId.isEmpty()) { clearPending(); notice = "Delivery uncertainty acknowledged. No request resent."; changed(); }
    }
    void cancel() {
        if (!connected || selected.isEmpty()) return;
        final String session = selected;
        request(() -> wire.call("session.cancel", object("sessionId", session)), value -> {
            notice = "Cancellation requested; queued work may remain.";
        }, generation);
    }
    void answer(String id, JSONObject value) {
        if (!connected || !decisions.containsKey(id)) return;
        request(() -> wire.respond(id, value), result -> {
            if (result.getBoolean("accepted")) { decisions.remove(id); notice = "Decision accepted"; }
            else { notice = "Decision refused: " + result.optString("reason"); if ("not-pending".equals(result.optString("reason"))) decisions.remove(id); }
        }, generation);
    }
    private void receive(JSONObject frame) throws Exception {
        JSONObject payload = frame.getJSONObject("payload");
        String type = payload.getString("type");
        if ("stream/error".equals(type)) { lost(generation, "STREAM_PROTOCOL"); return; }
        if (type.startsWith("host/session-")) {
            hostRevision++;
            if (selected.equals(payload.optString("sessionId")) && "host/session-status".equals(type)) running = payload.getBoolean("running");
            if (!"host/session-status".equals(type)) refreshSessions();
        }
        if (!selected.equals(payload.optString("sessionId"))) return;
        if ("session/snapshot".equals(type)) {
            refreshModel();
            JSONObject snapshot = payload.getJSONObject("snapshot");
            JSONArray records = snapshot.getJSONArray("records");
            for (int i = 0; i < records.length(); i++) conversation.add(records.getJSONObject(i).getJSONObject("event"));
            conversation.hasMore = snapshot.getBoolean("hasMore");
            conversation.liveSnapshot(snapshot.optJSONObject("assistantStream"));
            JSONObject projections = snapshot.getJSONObject("projections");
            lateQuestions(projections.getJSONObject("values"), projections.getLong("asOfSeq"));
            reconcilePending();
        } else if ("session/projections".equals(type)) {
            JSONObject modelValues=payload.getJSONObject("values");
            if(modelValues.has("modelSelection")||modelValues.has("contextPressure"))refreshModel();
            lateQuestions(payload.getJSONObject("values"), payload.getLong("asOfSeq"));
        } else if ("session/assistant-stream".equals(type)) {
            conversation.liveFrame(payload.getJSONObject("frame"));
        } else if ("session/event".equals(type)) {
            String eventType=payload.getJSONObject("event").optString("type");
            if("model/selection".equals(eventType)||"request/context".equals(eventType)||"request/header".equals(eventType))refreshModel();
            conversation.add(payload.getJSONObject("event"));
            reconcilePending();
        } else if ("approval/requested".equals(type) || "question/requested".equals(type)) {
            decisions.put(frame.getString("rpcId"), frame);
        } else if ("approval/resolved".equals(type)) {
            decisions.entrySet().removeIf(entry -> payload.optString("approvalId").equals(entry.getValue().optJSONObject("payload").optString("approvalId")));
        } else if ("question/resolved".equals(type)) decisions.remove(payload.getString("questionRpcId"));
        else if ("session/queue".equals(type)) {
            JSONArray items = payload.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject source = items.getJSONObject(i).getJSONObject("message").optJSONObject("source");
                if (source != null && pendingId.equals(source.optString("rpcId")) && !pendingId.isEmpty()) clearPending();
            }
        } else if ("host/agent-error".equals(type)) notice = "Backend task failed: " + payload.optString("message");
        changed();
    }
    String taskState() {
        if (!connected) return "Task state unknown while disconnected";
        for (JSONObject frame : decisions.values()) if ("approval/requested".equals(frame.optJSONObject("payload").optString("type"))) return "waiting-for-approval";
        for (String id : decisions.keySet()) if (!id.startsWith("continued:")) return "waiting-for-user";
        return running ? "running" : conversation.terminal();
    }
    private void lateQuestions(JSONObject values, long seq) throws Exception {
        JSONObject questions = values.optJSONObject("userQuestions");
        if (questions == null || seq < questionSeq) return;
        questionSeq = seq;
        decisions.entrySet().removeIf(entry -> entry.getKey().startsWith("continued:"));
        JSONArray active = questions.getJSONArray("active");
        for (int i = 0; i < active.length(); i++) {
            JSONObject item = active.getJSONObject(i);
            if (!"continued".equals(item.getString("state"))) continue;
            String id = "continued:" + item.getString("callId");
            decisions.put(id, object("type", "server-request", "rpcId", id, "payload", object("type", "question/requested",
                "sessionId", selected, "questions", item.getJSONArray("questions"), "continued", true)));
        }
    }
    void destroy() { disconnect(); destroyed = true; preferences.unregisterOnSharedPreferenceChangeListener(backendChanges); io.shutdownNow(); main.removeCallbacksAndMessages(null); }
}
