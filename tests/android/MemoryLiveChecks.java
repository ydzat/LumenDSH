package local.sliverwolf.assistant;

import android.content.SharedPreferences;
import org.json.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Runs the production memory transport against the phone's authenticated host. */
public final class MemoryLiveChecks {
    public static void main(String[] args) throws Exception {
        JSONObject config=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));
        SharedPreferences preferences=(SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
            new Class<?>[]{SharedPreferences.class},(proxy,method,values)->{
                if("getInt".equals(method.getName()))return config.getInt("port");
                if("getString".equals(method.getName()))return "backendCookie".equals(values[0])?config.getString("cookie"):"0.2.1-rc.3";
                throw new UnsupportedOperationException(method.getName());
            });
        String session=new String(Files.readAllBytes(Paths.get(args[1])),StandardCharsets.UTF_8).trim();
        DshTransport wire=new DshTransport(preferences);
        JSONObject status=wire.memoryRead("status-summary",session);
        if(!"0.5.34".equals(status.getString("dshMnemonVersion")))throw new AssertionError("Plugin version mismatch");
        JSONObject semantic=wire.memoryRead("embedding-status",session);
        if(!semantic.getBoolean("available") || semantic.getInt("embedded")!=semantic.getInt("totalInsights"))
            throw new AssertionError("Semantic coverage incomplete");
        JSONArray records=wire.memoryRead("list",session).getJSONArray("items");
        boolean found=false;
        for(int i=0;i<records.length();i++)if(records.getJSONObject(i).getString("content").contains("M7_ARCHIVE_STAR"))found=true;
        if(!found)throw new AssertionError("Durable memory absent after restart");
        if(wire.memoryRead("runtime-memory",session).getJSONArray("entries").toString().contains("M7_TEST_LANTERN"))throw new AssertionError("Forgotten hot memory remains");
        if(wire.memoryRead("list",args[2]).toString().contains("M7_ARCHIVE_STAR"))throw new AssertionError("Test memory leaked into owner workspace");
        JSONObject behavior=wire.companion("status",null);
        if(behavior.getJSONObject("policy").getInt("schemaVersion")!=1)throw new AssertionError("Behavior contract absent");
        JSONArray docs=wire.memoryRead("documents",session).getJSONArray("documents");
        if(docs.length()==0)throw new AssertionError("Real consolidation document absent");
        JSONObject doc=wire.memoryRead("document",session,docs.getJSONObject(0).getString("id"));
        if(doc.getString("content").isEmpty()||!doc.getJSONArray("sessionIds").toString().contains(session))
            throw new AssertionError("Document body or session provenance absent");
        System.out.println("M7_JAVA_STATUS_DURABLE_RESTART_FORGET_ISOLATION_OK");
        System.out.println("M7_JAVA_BEHAVIOR_DOCUMENT_PROVENANCE_OK");
    }
}
