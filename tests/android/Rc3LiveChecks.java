package local.sliverwolf.assistant;

import android.content.SharedPreferences;
import org.json.*;
import org.java_websocket.client.WebSocketClient;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static local.sliverwolf.assistant.DshTransport.object;

/** Real phone loopback checks using production transport; no server or model simulation. */
public final class Rc3LiveChecks {
    static boolean assistantContains(Conversation conversation, String marker) {
        for(JSONObject event:conversation.events.values())if("assistant/message".equals(event.optString("type"))) {
            JSONObject message=event.optJSONObject("data").optJSONObject("message");
            if(message!=null&&message.toString().contains(marker))return true;
        }
        return false;
    }
    public static void main(String[] args) throws Exception {
        // The launcher-created browser cookie remains on the phone and is never printed.
        JSONObject config = new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8));
        SharedPreferences preferences = (SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
            new Class<?>[]{SharedPreferences.class}, (proxy, method, values) -> {
                if ("getInt".equals(method.getName())) return config.getInt("port");
                if ("getString".equals(method.getName())) return "backendCookie".equals(values[0]) ? config.getString("cookie") : "0.2.1-rc.3";
                throw new UnsupportedOperationException(method.getName());
            });
        DshTransport wire = new DshTransport(preferences);
        boolean tools = args.length > 2 && "--tools".equals(args[2]);
        boolean questions = args.length > 2 && "--question".equals(args[2]);
        JSONArray listed = wire.call("session.list", object()).getJSONArray("items");
        System.out.println("LIVE_AUTH_LIST_OK count=" + listed.length());
        String session = wire.call("session.create", object("cwd", args[1])).getString("sessionId");
        wire.call("session.rename", object("sessionId", session, "title", "Controlled rc.3 client verification"));
        Conversation conversation = new Conversation();
        CountDownLatch ready = new CountDownLatch(2), terminal = new CountDownLatch(1);
        List<String> failures = Collections.synchronizedList(new ArrayList<>());
        int[] chunks = {0}, approvals = {0}, lateAnswers = {0};
        DshTransport.Events listener = new DshTransport.Events() {
            public void opened() { ready.countDown(); }
            public void lost() { failures.add("stream-lost"); ready.countDown(); terminal.countDown(); }
            public void frame(JSONObject frame) {
                try { synchronized(conversation) {
                    JSONObject p = frame.getJSONObject("payload");
                    String type = p.getString("type");
                    if ("session/snapshot".equals(type)) {
                        JSONObject snapshot = p.getJSONObject("snapshot");
                        JSONArray records = snapshot.getJSONArray("records");
                        for(int i=0;i<records.length();i++)conversation.add(records.getJSONObject(i).getJSONObject("event"));
                        conversation.liveSnapshot(snapshot.optJSONObject("assistantStream"));
                    } else if ("session/assistant-stream".equals(type)) { conversation.liveFrame(p.getJSONObject("frame")); chunks[0]++; }
                    else if ("approval/requested".equals(type) && session.equals(p.optString("sessionId"))) {
                        String name = p.optString("toolName");
                        boolean safe = name.equals("mcp__sliver_wolf__assistant_get_status");
                        if (name.equals("bash")) for(JSONObject event:conversation.events.values()) {
                            JSONObject data = event.optJSONObject("data");
                            if(data==null||!"tool/call".equals(event.optString("type"))||!p.optString("callId").equals(data.optString("callId")))continue;
                            JSONObject parameters=new JSONObject(data.getString("arguments"));
                            safe=parameters!=null&&parameters.optString("command").matches("printf ['\"]?RC3_TOOL_OK(\\\\n)?['\"]?");
                        }
                        if(!safe)throw new IllegalStateException("Unexpected approval tool or command");
                        wire.respond(frame.getString("rpcId"),object("sessionId",session,"approvalId",p.getString("approvalId"),"outcome","allowed-once"));
                        approvals[0]++;
                    }
                    else if (questions && "question/requested".equals(type) && session.equals(p.optString("sessionId"))) {
                        JSONObject item=p.getJSONArray("questions").getJSONObject(0);
                        if(!"upgrade_probe".equals(item.getString("id")))throw new IllegalStateException("Unexpected test question");
                        JSONObject reply=wire.respond(frame.getString("rpcId"),object("sessionId",session,"answer",object("answers",new JSONArray().put(object("id","upgrade_probe","selected",new JSONArray().put("Yes"))))));
                        if(!reply.getBoolean("accepted"))throw new IllegalStateException("Question answer refused");
                        lateAnswers[0]++;
                    }
                    else if (questions && "session/projections".equals(type)) {
                        JSONObject projection=p.getJSONObject("values").optJSONObject("userQuestions");
                        JSONArray active=projection==null?null:projection.optJSONArray("active");
                        if(active!=null && lateAnswers[0]==0)for(int i=0;i<active.length();i++) {
                            JSONObject q=active.getJSONObject(i);
                            if(!"continued".equals(q.optString("state")))continue;
                            JSONObject item=q.getJSONArray("questions").getJSONObject(0);
                            if(!"upgrade_probe".equals(item.getString("id")))throw new IllegalStateException("Unexpected test question");
                            JSONObject reply=wire.respond("continued:"+q.getString("callId"),object("sessionId",session,"answer",object("answers",new JSONArray().put(object("id","upgrade_probe","selected",new JSONArray().put("Yes"))))));
                            if(!reply.getBoolean("accepted"))throw new IllegalStateException("Late answer refused");
                            lateAnswers[0]++;
                        }
                    }
                    else if ("session/event".equals(type)) {
                        JSONObject event = p.getJSONObject("event"); conversation.add(event);
                        if("turn/end".equals(event.getString("type")) && (!questions || assistantContains(conversation,"RC3_QUESTION_OK")))terminal.countDown();
                    }
                }} catch(Exception error) {failures.add(error.getClass().getSimpleName());terminal.countDown();}
            }
        };
        WebSocketClient follow = wire.stream("events.mux", session, listener), host = wire.stream("events.host", session, listener);
        try {
            if(!ready.await(20,TimeUnit.SECONDS)||!failures.isEmpty())throw new AssertionError("Follow/host readiness failed " + failures);
            String id = UUID.randomUUID().toString();
            String text = questions ? "Controlled upgrade test. Call ask_user_question with exactly one question id=upgrade_probe, question=Continue this controlled check?, options Yes and No. After the answer arrives, reply exactly RC3_QUESTION_OK. Use no other tool."
                : tools ? "Controlled upgrade check. Call bash with command='printf RC3_TOOL_OK', sandbox_permissions='danger-full-access', justification='Print the controlled upgrade marker; Android does not support the default command sandbox.'. Wait for the one-time approval. Then call mcp__sliver_wolf__assistant_get_status with empty arguments. Do not run any other command or diagnose the environment. If a tool fails, stop and report it. Otherwise reply exactly RC3_TOOLS_OK."
                : "Reply with exactly: RC3_CLIENT_OK. Do not use tools.";
            JSONObject prompt = object("sessionId",session,"mode","queue","content",new JSONArray().put(object("type","text","text",text)));
            if(!wire.call("session.prompt",prompt,id).getBoolean("accepted"))throw new AssertionError("Prompt not accepted");
            if(!wire.call("session.prompt",prompt,id).getBoolean("accepted"))throw new AssertionError("Duplicate ID not acknowledged");
            if(!terminal.await(120,TimeUnit.SECONDS))throw new AssertionError("No terminal event");
            synchronized(conversation) {
                if(!failures.isEmpty())throw new AssertionError("Stream failure " + failures);
                long requests=0;
                for(JSONObject event:conversation.events.values()){
                    JSONObject data=event.optJSONObject("data");
                    JSONObject source=data==null?null:data.optJSONObject("source");
                    if("user/message".equals(event.optString("type"))&&source!=null&&id.equals(source.optString("rpcId")))requests++;
                }
                if(requests!=1||!conversation.containsRequest(id))throw new AssertionError("Prompt identity/deduplication failed");
                if(args.length>2&&("--model".equals(args[2])||tools||questions)) {
                    if(!conversation.terminal().equals("idle · completed")||!assistantContains(conversation,questions?"RC3_QUESTION_OK":tools?"RC3_TOOLS_OK":"RC3_CLIENT_OK")||chunks[0]==0)
                        throw new AssertionError("Real model completion/streaming missing");
                }
                if(questions && lateAnswers[0]!=1)throw new AssertionError("Late answer missing");
                if(questions)System.out.println("LIVE_QUESTION_ANSWER_OK");
                if(tools) {
                    boolean bash=false,mcp=false;
                    for(JSONObject event:conversation.events.values())if("tool/call".equals(event.optString("type"))) {
                        String name=event.getJSONObject("data").optString("name");
                        bash|="bash".equals(name);mcp|="mcp__sliver_wolf__assistant_get_status".equals(name);
                    }
                    if(!bash||!mcp||!conversation.text().contains("0.7.40-dsh-rc3"))throw new AssertionError("Real Bash/MCP result missing");
                    if(approvals[0] != 1)throw new AssertionError("Expected one actual approval");
                    System.out.println("LIVE_BASH_MCP_OK approvals="+approvals[0]);
                }
                System.out.println("LIVE_PROMPT_DEDUP_FOLLOW_OK terminal="+conversation.terminal()+" streamFrames="+chunks[0]);
            }
            JSONObject page=wire.call("session.history",object("sessionId",session,"maxMessages",50));
            if(page.getJSONArray("events").length()==0)throw new AssertionError("Empty history");
            System.out.println("LIVE_PAGE_OK");
        } finally { follow.closeConnection(1000, "Probe complete"); host.closeConnection(1000, "Probe complete"); }
    }
}
