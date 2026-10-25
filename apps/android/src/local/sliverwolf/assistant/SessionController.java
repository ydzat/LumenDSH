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
    private final Runnable reconnect = () -> connect();
    private void backendChanged() { if (!stopped && !destroyed) connect(); }

    SessionController(SharedPreferences preferences, Listener listener) {
        this.preferences = preferences;
        wire = new DshTransport(preferences);
        preferences.registerOnSharedPreferenceChangeListener(backendChanges);
        this.listener = listener;
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
        connection = "Connecting";
        decisions.clear();
        conversation.clear();
        questionSeq = -1;
        running = false;
        changed();
        mux = socket("events.mux", epoch);
        host = socket("events.host", epoch);
        request(() -> wire.call("host.describe", object()), value -> {
            describeReady = true;
            ready();
        }, epoch);
    }
    void disconnect() {
        stopped = true;
        closeGeneration();
        connection = "Disconnected · DSH continues running";
        decisions.clear();
        changed();
    }
    private void closeGeneration() {
        generation++;
        modelInfo=null;modelError="未连接 · 不使用缓存模型配置";modelBusy=false;modelAgain=false;
        main.removeCallbacks(reconnect);
        connected = false; opened = 0; describeReady = false; historyBusy = false; listBusy = false; creating = false;
        if (mux != null) mux.close();
        if (host != null) host.close();
        mux = null; host = null;
    }
    private WebSocketClient socket(String channel, int epoch) {
        return wire.stream(channel, selected, new DshTransport.Events() {
            public void opened() { main.post(() -> { if (current(epoch)) { opened++; ready(); } }); }
            public void frame(JSONObject frame) { main.post(() -> {
                if (!current(epoch)) return;
                try { receive(frame); } catch (Exception error) { SessionController.this.lost(epoch, "Invalid event or history limit"); }
            }); }
            public void lost() { main.post(() -> SessionController.this.lost(epoch, "Event connection lost")); }
        });
    }
    private void ready() {
        if (connected || opened != 2 || !describeReady) return;
        connected = true; failures = 0; connection = "Connected · local DSH rc.3 · authenticated";
        refreshSessions();
        refreshModel();
        changed();
    }
    private void lost(int epoch, String message) {
        if (!current(epoch)) return;
        closeGeneration();
        decisions.clear();
        if (!pendingId.isEmpty()) pendingState = "delivery-unknown";
        connection = "Disconnected · " + message + " · reconnecting";
        main.postDelayed(reconnect, Math.min(30000, 1000L << Math.min(failures++, 5)));
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
                    if (error instanceof DshTransport.Refused) { notice = "Request refused: " + error.getMessage(); changed(); }
                    else lost(epoch, "Request failed (" + error.getClass().getSimpleName() + ")");
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
        if ("stream/error".equals(type)) { lost(generation, "Backend stream error"); return; }
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
