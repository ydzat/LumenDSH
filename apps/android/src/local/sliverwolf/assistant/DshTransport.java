package local.sliverwolf.assistant;

import org.json.JSONObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

/** Authenticated rc.3 loopback transport; never manages the backend process. */
final class DshTransport {
    static final String BASE = "http://127.0.0.1:3080";
    static final int LIMIT = 8 * 1024 * 1024;
    private final android.content.SharedPreferences preferences;
    private volatile Rc3Protocol modern;
    DshTransport(android.content.SharedPreferences preferences) { this.preferences = preferences; refresh(); }
    void refresh() {
        modern = new Rc3Protocol();
    }
    JSONObject companion(String operation, JSONObject body) throws Exception {
        if (!"status".equals(operation) && !"policy".equals(operation) && !"restore".equals(operation) && !"model-selection".equals(operation) && !operation.startsWith("model?sessionId="))
            throw new IllegalArgumentException("Unknown companion operation");
        HttpURLConnection connection=(HttpURLConnection)new URL(base()+"/sliver-wolf/companion/"+operation).openConnection(Proxy.NO_PROXY);
        connection.setConnectTimeout(5000); connection.setReadTimeout(15000); connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Cookie",cookie());
        try {
            if(body!=null) {
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type","application/json");
                byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8); connection.setFixedLengthStreamingMode(bytes.length);
                try(java.io.OutputStream out=connection.getOutputStream()){out.write(bytes);}
            }
            int status=connection.getResponseCode();
            if(status!=200) throw new IOException("Companion HTTP "+status+"; reload settings before retrying");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=connection.getInputStream()) {
                byte[] chunk=new byte[8192];int count;
                while((count=in.read(chunk))!=-1) {if(bytes.size()+count>512*1024)throw new IOException("Companion response too large");bytes.write(chunk,0,count);}
            }
            return new JSONObject(bytes.toString("UTF-8"));
        } finally {connection.disconnect();}
    }
    JSONObject model(String session) throws Exception {
        if(session==null||session.isEmpty())throw new IOException("Select a conversation first");
        return companion("model?sessionId="+java.net.URLEncoder.encode(session,"UTF-8"),null);
    }
    private String base() { return "http://127.0.0.1:" + preferences.getInt("backendPort", 3080); }
    private String cookie() { return preferences.getString("backendCookie", ""); }
    interface Events { void opened(); void frame(JSONObject frame); void lost(); default void lost(String reason){lost();} }
    static final class HttpFailure extends IOException {
        final int status; HttpFailure(int status){super("HTTP "+status);this.status=status;}
    }
    static String failureCode(Throwable error) {
        for(Throwable e=error;e!=null;e=e.getCause()) {
            if(e instanceof HttpFailure)return "HTTP_"+((HttpFailure)e).status;
            if(e instanceof java.net.ConnectException)return "SERVICE_UNREACHABLE";
            if(e instanceof java.net.SocketTimeoutException)return "TIMEOUT";
            if(e instanceof Refused)return "RPC_REFUSED";
            if(e instanceof org.json.JSONException)return "PROTOCOL";
        }
        return "TRANSPORT_OR_PROTOCOL";
    }
    static final class Refused extends IOException {
        Refused(String code) { super(code); }
    }
    static JSONObject object(Object... fields) {
        JSONObject value = new JSONObject();
        try {
            for (int i = 0; i < fields.length; i += 2) value.put((String) fields[i], fields[i + 1]);
        } catch (org.json.JSONException error) { throw new IllegalArgumentException(error); }
        return value;
    }
    JSONObject call(String method, JSONObject payload) throws Exception {
        return call(method, payload, UUID.randomUUID().toString());
    }
    JSONObject call(String method, JSONObject payload, String id) throws Exception {
        Rc3Protocol protocol = modern;
        String original = method;
        {
            if ("host.describe".equals(method)) return call("session.list", object(), id);
            payload = protocol.arguments(method, payload, id);
            method = "session.history".equals(method) ? "session/page" : method.replace('.', '/');
        }
        JSONObject reply = post(method, object("type", "client-request", "rpcId", id,
            "method", method, "payload", payload));
        if (!"server-response".equals(reply.getString("type")) || !id.equals(reply.getString("rpcId")))
            throw new IOException("RPC envelope mismatch");
        JSONObject result = reply.getJSONObject("result");
        if (!result.getBoolean("ok")) throw new Refused(result.getJSONObject("error").getString("code"));
        if ("userQuestions.answer".equals(original)) return object("accepted", result.getBoolean("value"), "reason", "not-pending");
        JSONObject value = result.optJSONObject("value");
        if (value == null) value = object();
        return protocol.result(original, value);
    }
    JSONObject respond(String id, JSONObject value) throws Exception {
        if (id.startsWith("continued:")) return call("userQuestions.answer", object("agentId", value.getString("sessionId"),
            "callId", id.substring("continued:".length()), "answer", value.getJSONObject("answer")));
        Rc3Protocol protocol = modern;
        {
            JSONObject payload = protocol.answer(id, value);
            String rpcId = UUID.randomUUID().toString();
            JSONObject reply = post("$events/result", object("type", "client-request", "rpcId", rpcId,
                "method", "$events/result", "payload", object("args", payload)));
            if (!"server-response".equals(reply.optString("type")) || !rpcId.equals(reply.optString("rpcId"))) throw new IOException("RPC envelope mismatch");
            JSONObject result = reply.getJSONObject("result");
            if (!result.getBoolean("ok")) throw new Refused(result.getJSONObject("error").optString("code", "decision-refused"));
            return object("accepted", true);
        }
    }
    /** Mnemon 0.5.34 exposes a nested result through the authenticated DSH gateway. */
    JSONObject memoryRead(String endpoint, String session) throws Exception {
        return memoryRead(endpoint,session,null);
    }
    JSONObject memoryRead(String endpoint, String session, String documentId) throws Exception {
        if (session == null || session.isEmpty()) throw new IOException("Select a conversation first");
        if (!"status-summary".equals(endpoint) && !"runtime-memory".equals(endpoint) && !"list".equals(endpoint) && !"embedding-status".equals(endpoint)
                && !"documents".equals(endpoint) && !"document".equals(endpoint))
            throw new IllegalArgumentException("Unsupported memory read");
        if("document".equals(endpoint) && (documentId==null||documentId.isEmpty()||documentId.length()>300))
            throw new IllegalArgumentException("Document ID required");
        // Cwd-created sessions are not necessarily accounted in DSH's workspace
        // registry. Mnemon otherwise falls back to the host workspace after restart.
        org.json.JSONArray sessions=call("session.list",object()).getJSONArray("items");
        String cwd=null;
        for(int i=0;i<sessions.length();i++) {
            JSONObject item=sessions.getJSONObject(i);
            if(session.equals(item.optString("sessionId"))) cwd=item.optString("cwd",null);
        }
        if(cwd==null || cwd.isEmpty()) throw new IOException("Conversation workspace unavailable");
        String workspace=call("workspace.create",object("path",cwd)).getJSONObject("workspace").getString("workspaceId");
        String id = UUID.randomUUID().toString();
        String method = "dshMnemon/read";
        JSONObject payload=object("sessionId",session,"workspaceId",workspace,"limit",100);
        if(documentId!=null)payload.put("id",documentId);
        JSONObject reply = post(method, object("type", "client-request", "rpcId", id, "method", method,
            "payload", object("args", object("endpoint", endpoint, "payload", payload))));
        if (!"server-response".equals(reply.optString("type")) || !id.equals(reply.optString("rpcId")))
            throw new IOException("Memory RPC envelope mismatch");
        JSONObject outer = reply.getJSONObject("result");
        if (!outer.getBoolean("ok")) throw new Refused(outer.getJSONObject("error").optString("code", "memory-unavailable"));
        JSONObject inner = outer.getJSONObject("value");
        if (!inner.getBoolean("ok")) throw new Refused(inner.getJSONObject("error").optString("code", "memory-unavailable"));
        return inner.getJSONObject("value");
    }
    private JSONObject post(String endpoint, JSONObject request) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(base() + "/api/" + endpoint).openConnection(Proxy.NO_PROXY);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Cookie", cookie());
        connection.setDoOutput(true);
        byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(body); }
            if (connection.getResponseCode() != 200) throw new HttpFailure(connection.getResponseCode());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] chunk = new byte[8192];
                int count;
                while ((count = input.read(chunk)) != -1) {
                    if (bytes.size() + count > LIMIT) throw new IOException("Response limit exceeded");
                    bytes.write(chunk, 0, count);
                }
            }
            return new JSONObject(bytes.toString("UTF-8"));
        } finally { connection.disconnect(); }
    }
    WebSocketClient stream(String channel, String session, Events events) {
        final Rc3Protocol protocol = modern;
        final boolean host = "events.host".equals(channel);
        WebSocketClient socket = new WebSocketClient(URI.create(base().replace("http:", "ws:") + "/api/" + "remote.mux"),
                new Draft_6455(Collections.emptyList(), LIMIT), Collections.singletonMap("Cookie", cookie()), 5000) {
            private boolean ready;
            @Override public void onOpen(ServerHandshake handshake) {
                if (!host && session.isEmpty()) { ready = true; events.opened(); return; }
                send(protocol.open(host, session).toString());
                send(object("type", "end", "streamId", "main").toString());
                if (host) {
                    send(protocol.controlOpen().toString());
                    send(object("type", "end", "streamId", "control").toString());
                }
            }
            @Override public void onMessage(String text) {
                try {
                    if (text.length() > LIMIT) throw new IOException("Frame limit exceeded");
                    JSONObject frame = new JSONObject(text);
                    {
                        if (host && "control".equals(frame.optString("streamId"))) {
                            if (!"item".equals(frame.optString("type"))) throw new IOException("Control stream ended");
                            JSONObject translated = protocol.control(session, frame.getJSONObject("value"));
                            if (translated != null) events.frame(translated);
                            return;
                        }
                        if (!"main".equals(frame.optString("streamId"))) throw new IOException("Unexpected stream");
                        if (!"item".equals(frame.optString("type"))) throw new IOException("Stream ended");
                        JSONObject value = frame.getJSONObject("value");
                        JSONObject translated = protocol.frame(host, session, value);
                        if (translated != null) events.frame(translated);
                        if (!ready && (host ? "ready" : "snapshot").equals(value.optString("type"))) { ready = true; events.opened(); }
                        return;
                    }
                } catch (Exception error) { events.lost("STREAM_PROTOCOL"); close(); }
            }
            @Override public void onClose(int code, String reason, boolean remote) { events.lost("WS_CLOSE_"+code); }
            @Override public void onError(Exception error) { events.lost(failureCode(error)); }
        };
        socket.setConnectionLostTimeout(20);
        socket.connect();
        return socket;
    }
}
