package local.sliverwolf.assistant;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import static local.sliverwolf.assistant.DshTransport.object;

/** Local authenticated content receipts. No APK, engine, credentials or arbitrary paths. */
final class ContentReleases {
    private final Context context;
    private final File root;
    ContentReleases(Context context)throws Exception{
        this.context=context.getApplicationContext();root=new File(context.getFilesDir(),"content-releases");
        if(!root.isDirectory()&&!root.mkdirs())throw new java.io.IOException("Cannot create release journal");
    }
    private File file(String id){
        if(id==null||!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))throw new IllegalArgumentException("Invalid releaseId");
        return new File(root,id+".json");
    }
    private JSONObject read(String id)throws Exception{
        byte[] bytes=new AtomicFile(file(id)).readFully();
        if(bytes.length>65536)throw new java.io.IOException("Release journal too large");
        JSONObject record=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(!id.equals(record.getString("releaseId")))throw new java.io.IOException("Release journal identity mismatch");
        return record;
    }
    private void write(JSONObject record)throws Exception{
        byte[] encoded=record.toString().getBytes(StandardCharsets.UTF_8);
        if(encoded.length>65536)throw new IllegalArgumentException("Release receipt too large");
        AtomicFile atomic=new AtomicFile(file(record.getString("releaseId")));FileOutputStream out=null;
        try{out=atomic.startWrite();out.write(encoded);atomic.finishWrite(out);}
        catch(Exception error){if(out!=null)atomic.failWrite(out);throw error;}
        if(!read(record.getString("releaseId")).toString().equals(record.toString()))throw new java.io.IOException("Release receipt readback mismatch");
    }
    private String current() {return PresentationSettings.stored(context);}
    private PetStore pets()throws Exception{return new PetStore(new File(context.getFilesDir(),"appearances"));}
    private AssistantProfile.Store profiles()throws Exception{return new AssistantProfile.Store(new File(context.getFilesDir(),"assistant-profiles"));}
    void baselineVoice(AssistantProfile.Voice voice)throws Exception{
        android.content.SharedPreferences prefs=context.getSharedPreferences("release-baseline",0);
        if(!prefs.contains("voice")&&!prefs.edit().putString("voice",voice.json().toString()).commit())throw new java.io.IOException("Cannot save voice baseline");
    }
    private String current(String component)throws Exception{
        if(component.equals("presentation"))return current();
        if(!component.equals("profile"))throw new IllegalArgumentException("Unknown component");
        PetStore store=pets();String key=store.activeProfile();
        if(!key.isEmpty())return profiles().load(key).json().toString();
        PetStore.Pack pack=store.restore();String appearance=pack==null?"":pack.key;if(pack!=null)pack.sprite.bitmap.recycle();
        String voice=context.getSharedPreferences("release-baseline",0).getString("voice","");
        return new AssistantProfile("Current profile",appearance,AssistantProfile.Voice.parse(new JSONObject(voice))).json().toString();
    }
    private void validatePayload(String component,String payload)throws Exception{
        if(component.equals("presentation")){PresentationSettings.parse(payload);return;}
        if(!component.equals("profile")||payload.getBytes(StandardCharsets.UTF_8).length>8192)throw new IllegalArgumentException("Unsupported component/payload");
        AssistantProfile profile=AssistantProfile.parse(new JSONObject(payload));
        if(!profile.json().toString().equals(payload))throw new IllegalArgumentException("Use assistant_prepare_profile for the canonical profile payload");
        if(!profile.appearance.isEmpty()){PetStore.Pack pack=pets().load(profile.appearance);pack.sprite.bitmap.recycle();}
    }
    private void save(String component,String payload)throws Exception{
        if(component.equals("presentation")){PresentationSettings.save(context,payload);return;}
        AssistantProfile profile=AssistantProfile.parse(new JSONObject(payload));
        PetStore store=pets();PetStore.Pack pack=profile.appearance.isEmpty()?null:store.load(profile.appearance);
        try{store.activate(pack,profiles().save(profile),profile);}finally{if(pack!=null)pack.sprite.bitmap.recycle();}
    }
    synchronized JSONObject component(String component)throws Exception{return object("componentId",component,"currentPayload",current(component),"currentVersion",hash(current(component)));}
    synchronized JSONObject pending()throws Exception{
        for(String id:ids()){JSONObject record=read(id);if("verifying".equals(record.optString("state")))return record;}return null;
    }
    synchronized JSONObject confirm(String id,boolean healthy,String detail)throws Exception{
        JSONObject record=read(id);if(!"verifying".equals(record.optString("state")))return view(record);
        JSONObject manifest=record.getJSONObject("manifest");
        if(healthy&&hash(current(manifest.getString("componentId"))).equals(manifest.getString("targetVersion"))){
            record.put("state","active").put("detail",detail);write(record);
        }else restore(record,"Runtime health failed: "+detail);
        return view(record);
    }
    synchronized boolean unsettled()throws Exception{
        for(String id:ids())if(java.util.Arrays.asList("activating","verifying","recovering","recovery-required").contains(read(id).optString("state")))return true;return false;
    }
    private static String hash(String text)throws Exception{return PresentationSettings.digest(text);}
    private long code()throws Exception{return context.getPackageManager().getPackageInfo(context.getPackageName(),0).getLongVersionCode();}
    synchronized JSONObject snapshot()throws Exception{
        JSONArray releases=new JSONArray();
        for(String id:ids())releases.put(view(read(id)));
        return object("componentId","presentation","currentVersion",hash(current()),"currentPayload",current(),"releases",releases);
    }
    private java.util.List<String> ids()throws Exception{
        File[] files=root.listFiles();if(files==null)throw new java.io.IOException("Cannot list releases");
        java.util.Set<String> ids=new java.util.TreeSet<>();
        for(File f:files){String n=f.getName();if(n.endsWith(".json.bak"))n=n.substring(0,n.length()-4);if(n.endsWith(".json"))ids.add(n.substring(0,n.length()-5));}
        return new java.util.ArrayList<>(ids);
    }
    private void validate(JSONObject manifest)throws Exception{
        DeviceBridge.arguments(manifest,"schemaVersion","releaseId","componentId","baseVersion","targetVersion","payload","minClientCode","activationMode","verificationNote");
        file(manifest.getString("releaseId"));
        if(manifest.length()!=9||manifest.getInt("schemaVersion")!=1||!java.util.Arrays.asList("presentation","profile").contains(manifest.getString("componentId"))||!"reload".equals(manifest.getString("activationMode")))
            throw new IllegalArgumentException("Unsupported release format/component/activation");
        if(!manifest.getString("baseVersion").matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid base version");
        if(manifest.getLong("minClientCode")<1||manifest.getLong("minClientCode")>code())throw new IllegalArgumentException("Incompatible client version");
        String payload=manifest.getString("payload");validatePayload(manifest.getString("componentId"),payload);
        if(!hash(payload).equals(manifest.getString("targetVersion")))throw new IllegalArgumentException("Payload integrity mismatch");
        if(manifest.getString("verificationNote").length()>1000)throw new IllegalArgumentException("Verification note too long");
    }
    synchronized JSONObject stage(JSONObject manifest)throws Exception{
        if(manifest.toString().getBytes(StandardCharsets.UTF_8).length>24000)throw new IllegalArgumentException("Release manifest too large");
        String id=manifest.getString("releaseId");file(id);
        String digest=hash(manifest.toString());
        if(file(id).exists()||new File(file(id)+".bak").exists()){
            JSONObject previous=read(id);if(!digest.equals(previous.getString("manifestDigest")))throw new IllegalArgumentException("Release ID already names different content");
            return view(previous);
        }
        if(ids().size()>=32)throw new IllegalStateException("Release retention limit reached; inspect and discard terminal releases");
        JSONObject record=object("releaseId",id,"manifestDigest",digest,"manifest",manifest,"state","validating",
            "provenance","authenticated-local-device-bridge","createdAt",System.currentTimeMillis());
        write(record);
        try{
            validate(manifest);
            if(!hash(current(manifest.getString("componentId"))).equals(manifest.getString("baseVersion")))throw new IllegalArgumentException("Stale base version");
            record.put("state","staged").put("detail","Validated candidate; active configuration unchanged");
        }catch(Exception error){record.put("state","rejected").put("detail",error.getMessage());}
        write(record);return view(record);
    }
    synchronized JSONObject activate(String id,boolean safe)throws Exception{
        JSONObject record=read(id);
        if(!"staged".equals(record.getString("state")))return view(record);
        if(!safe)return view(record.put("detail","Waiting for foreground idle audio boundary; activate explicitly again"));
        if(unsettled())throw new IllegalStateException("Resolve the pending release first");
        JSONObject manifest=record.getJSONObject("manifest");
        try{
            validate(manifest);
            if(!hash(manifest.toString()).equals(record.getString("manifestDigest")))throw new IllegalArgumentException("Manifest integrity mismatch");
            if(!hash(current(manifest.getString("componentId"))).equals(manifest.getString("baseVersion")))throw new IllegalArgumentException("Stale base version");
        }catch(Exception error){record.put("state","rejected").put("detail",error.getMessage());write(record);return view(record);}
        record.put("previousPayload",current(manifest.getString("componentId"))).put("previousVersion",hash(current(manifest.getString("componentId")))).put("state","activating");write(record);
        try{
            save(manifest.getString("componentId"),manifest.getString("payload"));
            record.put("state","verifying").put("detail","Waiting for foreground rendering and voice health");write(record);
            if(!hash(current(manifest.getString("componentId"))).equals(manifest.getString("targetVersion")))throw new java.io.IOException("Active content readback mismatch");
        }catch(Exception error){restore(record,"Activation failed: "+error.getMessage());}
        return view(record);
    }
    private void restore(JSONObject record,String detail)throws Exception{
        record.put("state","recovering").put("detail",detail);write(record);
        try{
            String previous=record.getString("previousPayload");
            if(!hash(previous).equals(record.getString("previousVersion")))throw new java.io.IOException("Recovery content integrity mismatch");
            save(record.getJSONObject("manifest").getString("componentId"),previous);
            if(!hash(current(record.getJSONObject("manifest").getString("componentId"))).equals(record.getString("previousVersion")))throw new java.io.IOException("Recovery readback mismatch");
            record.put("state","recovered");
        }catch(Exception error){record.put("state","recovery-required").put("detail",detail+"; "+error.getMessage());}
        write(record);
    }
    synchronized void recover()throws Exception{
        for(String id:ids()){
            JSONObject record=read(id);String state=record.getString("state");
            if("validating".equals(state)){record.put("state","rejected").put("detail","Interrupted validation; active configuration unchanged");write(record);}
            else if("activating".equals(state)||"verifying".equals(state)||"recovering".equals(state))restore(record,"Interrupted activation recovered at startup");
        }
    }
    synchronized JSONObject rollback(String id,boolean safe)throws Exception{
        JSONObject record=read(id);if("recovered".equals(record.getString("state")))return view(record);
        if(!"active".equals(record.getString("state")))throw new IllegalStateException("Only an active release can roll back");
        if(!safe)throw new IllegalStateException("Wait for foreground idle audio before rollback");
        if(!hash(current(record.getJSONObject("manifest").getString("componentId"))).equals(record.getJSONObject("manifest").getString("targetVersion")))throw new IllegalStateException("Active configuration changed since this release");
        restore(record,"Explicit rollback");return view(record);
    }
    synchronized JSONObject discard(String id)throws Exception{
        JSONObject record=read(id);String state=record.getString("state");
        if(!("rejected".equals(state)||"recovered".equals(state)||"staged".equals(state)))throw new IllegalStateException("Cannot discard active or unresolved recovery release");
        new AtomicFile(file(id)).delete();return object("releaseId",id,"state","discarded");
    }
    private JSONObject view(JSONObject record)throws Exception{
        JSONObject manifest=record.optJSONObject("manifest");
        return object("releaseId",record.getString("releaseId"),"state",record.getString("state"),"detail",record.optString("detail"),
            "componentId",manifest==null?"":manifest.optString("componentId"),"baseVersion",manifest==null?"":manifest.optString("baseVersion"),
            "targetVersion",manifest==null?"":manifest.optString("targetVersion"),"provenance",record.optString("provenance"));
    }
}
