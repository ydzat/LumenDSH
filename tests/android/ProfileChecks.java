package local.sliverwolf.assistant;

import java.io.*;
import java.nio.file.Files;

/** Production storage/queue checks on Android; no claim about acoustic quality. */
public final class ProfileChecks {
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
    private interface Checked {void run() throws Exception;}
    private static void rejects(Checked action,String message) throws Exception {
        boolean rejected=false;try{action.run();}catch(Exception expected){rejected=true;}
        require(rejected,message);
    }
    public static void main(String[] args) throws Exception {
        File root=Files.createTempDirectory(new File(args[1]).toPath(),"profile-check-").toFile();
        PetStore pets=new PetStore(new File(root,"appearances"));
        AssistantProfile.Store profiles=new AssistantProfile.Store(new File(root,"profiles"));
        AssistantProfile.Voice voice=new AssistantProfile.Voice("com.google.android.tts","zh-CN",.75f,.85f);
        PetStore.Pack pack;
        try(InputStream in=new FileInputStream(new File(args[0],"valid-v2.zip"))){pack=pets.importZip(in);}
        AssistantProfile combined=new AssistantProfile("Robot slow",pack.key,voice);
        AssistantProfile imported=AssistantProfile.read(new ByteArrayInputStream(combined.json().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        require(imported.appearance.equals(pack.key)&&imported.voice.rate==voice.rate,"External composition retains exact references");
        rejects(()->AssistantProfile.read(new ByteArrayInputStream(new byte[8193])),"Reject oversized composition");
        rejects(()->AssistantProfile.read(new ByteArrayInputStream(new byte[]{(byte)0xc3,0x28})),"Reject malformed UTF-8");
        org.json.JSONObject unknown=combined.json().put("script","unused");
        rejects(()->AssistantProfile.read(new ByteArrayInputStream(unknown.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))),"Reject unknown executable or component fields");
        String key=profiles.save(combined);
        AssistantProfile loaded=profiles.load(key);
        require(loaded.appearance.equals(pack.key)&&loaded.voice.rate==.75f&&loaded.voice.pitch==.85f,"Exact components round trip");
        require(profiles.save(loaded).equals(key)&&profiles.list().size()==1,"Immutable idempotent save");
        pets.activate(pack,key,loaded);
        require(pets.activeProfile().equals(key)&&pets.restore().key.equals(pack.key),"Single pointer restores composition");
        AssistantProfile builtin=new AssistantProfile("Default","",new AssistantProfile.Voice(voice.engine,"zh-CN",1,1));
        String other=profiles.save(builtin);
        rejects(()->pets.activate(pack,other,builtin),"Reject mismatching appearance");
        rejects(()->pets.activate(pack,other,loaded),"Reject mismatching profile hash");
        require(pets.activeProfile().equals(key),"Failed activation retains original profile");
        rejects(()->profiles.remove(key,pets.activeProfile()),"Active profile cannot be removed");
        rejects(()->pets.remove(pack.key,profiles),"Active appearance cannot be removed");
        pets.activate(null,other,builtin);
        rejects(()->pets.remove(pack.key,profiles),"Inactive appearance still referenced by saved profile cannot be removed");
        profiles.remove(key,pets.activeProfile());
        pets.remove(pack.key,profiles);
        require(!pets.appearances().containsKey(pack.key),"Unreferenced appearance can be removed");
        try(InputStream in=new FileInputStream(new File(args[0],"valid-v2.zip"))){pets.importZip(in).sprite.bitmap.recycle();}
        require(profiles.save(combined).equals(key),"Deleted profile can be explicitly saved again");pets.activate(pack,key,combined);
        org.json.JSONObject tampered=loaded.json();
        tampered.getJSONObject("components").getJSONObject("voice").getJSONObject("config").put("rate",1.25);
        rejects(()->AssistantProfile.parse(tampered),"Reject voice digest mismatch");
        rejects(()->new AssistantProfile.Voice(voice.engine,"zh-CN",Float.NaN,1),"Reject nonfinite rate");
        rejects(()->new AssistantProfile.Voice(voice.engine,"zh-CN",1,2),"Reject pitch range");
        try(FileOutputStream out=new FileOutputStream(new File(root,"profiles/"+other+".json"),true)){out.write(32);}
        rejects(()->profiles.load(other),"Reject changed immutable record");
        require(pets.activeProfile().equals(key),"Bad stored candidate does not change active pointer");
        pets.activate(null);require(pets.activeProfile().isEmpty()&&pets.restore()==null,"Legacy built-in selection remains compatible");
        pack.sprite.bitmap.recycle();

        AttentionQueue queue=new AttentionQueue();queue.offer(1,"first");queue.pin(true);queue.offer(2,"second");
        for(int i=0;i<100;i++)queue.tick(1000,false);
        require(queue.current().sequence==1&&queue.pending()==1,"Reading keeps old text while new text waits");
        queue.pin(false);for(int i=0;i<100;i++)queue.tick(1000,true);
        require(queue.current().sequence==1,"Hidden/listening/speaking/decision time does not consume lifetime");
        for(int i=0;i<12;i++)queue.tick(1000,false);
        require(queue.current().sequence==2,"Visible lifetime advances backlog");
        queue.offer(2,"second");require(queue.pending()==0,"Reconciliation does not duplicate a sequence");
        queue.offer(3,"second");require(queue.pending()==1,"Distinct real messages may have identical text");
        for(int i=4;i<20;i++)queue.offer(i,"more");
        require(queue.pending()==8&&queue.overflow()==9,"Bounded presentation backlog retains overflow count");
        queue.acknowledgeOverflow();require(queue.overflow()==0&&queue.pending()==8,"Viewing overflow does not remove queued messages");
        queue.clear();queue.offer(20,"partial");queue.pin(true);queue.offer(20,"partial complete");
        require(queue.reading()&&queue.pending()==0&&queue.current().text.equals("partial complete"),"Streaming updates preserve pinned item");
        queue.next();queue.offer(20,"late completion");require(queue.current()==null,"Dismissed stream cannot return on completion");
        queue.offer(21,"waiting");queue.offer(22,"partial");queue.offer(22,"completed");
        require(queue.pending()==1,"Queued stream replaces text without duplicating item");
        queue.next();require(queue.current().text.equals("completed"),"Queued completion retains final text");
        queue.clear();require(queue.current()==null&&queue.pending()==0&&queue.overflow()==0,"Session reset clears presentation");
        System.out.println("PROFILE_AND_ATTENTION_CHECKS_PASS (Android JSON, AtomicFile, immutable components, attention timing)");
    }
}
