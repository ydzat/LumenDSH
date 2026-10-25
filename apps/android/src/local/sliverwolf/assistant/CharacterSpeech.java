package local.sliverwolf.assistant;

import android.content.Context;
import android.media.*;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Bounded sentence preparation/playback; DeviceSpeech owns focus and operation identity. */
final class CharacterSpeech {
    static final String ENGINE="lumendsh.silver_wolf.local";
    interface Listener {void state(String id,String state,String detail,boolean terminal);}
    private final Context context;private final VoiceProviders providers;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor(), cancellation=Executors.newSingleThreadExecutor();
    private volatile Task active;private MediaPlayer player;private boolean closed;
    private static final class Task {
        final String id;final VoiceProviders.Selection selection;final Listener listener;final float rate,pitch;
        final Semaphore slots=new Semaphore(2);final ArrayDeque<File> ready=new ArrayDeque<>();
        volatile boolean cancelled;volatile HttpURLConnection connection;volatile String requestId="";
        boolean downloaded;File playing;
        Task(String id,VoiceProviders.Selection s,float rate,float pitch,Listener l){this.id=id;selection=s;this.rate=rate;this.pitch=pitch;listener=l;}
    }
    CharacterSpeech(Context context){this.context=context;providers=new VoiceProviders(context);
        File[] stale=context.getCacheDir().listFiles((dir,name)->name.startsWith("character-voice-")&&name.endsWith(".wav"));
        if(stale!=null)for(File f:stale)f.delete();
    }
    boolean configured(){return !context.getSharedPreferences("character-voice",0).getString("token","").isEmpty();}
    boolean configured(String engine){return providers.configured(engine);}
    void pair(String token){
        if(!token.matches("[A-Za-z0-9_-]{40,100}"))throw new IllegalArgumentException("Invalid voice pairing");
        if(!context.getSharedPreferences("character-voice",0).edit().putString("token",token).commit())throw new IllegalStateException("Cannot save voice pairing");
    }
    static List<String> segments(String text,boolean remote){
        if(!remote)return Collections.singletonList(text);
        List<String> out=new ArrayList<>();int start=0;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);int length=i-start+1;
            if((length>=12&&"。！？!?；;\n".indexOf(c)>=0)||length>=70){
                if(Character.isHighSurrogate(c)&&i+1<text.length())continue;
                out.add(text.substring(start,i+1));start=i+1;
            }
        }
        if(start<text.length())out.add(text.substring(start));return out;
    }
    void speak(String engine,String id,String text,float rate,float pitch,Listener listener)throws Exception {
        if(closed||active!=null)throw new IllegalStateException("Speech provider busy");
        Task task=new Task(id,providers.resolve(engine),rate,pitch,listener);active=task;
        worker.execute(()->{
            try {
                int index=0;
                for(String part:segments(text,!"local".equals(task.selection.provider.id))){
                    while(!task.cancelled&&!task.slots.tryAcquire(200,TimeUnit.MILLISECONDS)){}
                    if(task.cancelled)return;
                    File file=fetch(task,part,id+"-"+(index++));
                    main.post(()->{if(task.cancelled||active!=task){file.delete();return;}task.ready.add(file);next(task);});
                }
                main.post(()->{if(active==task){task.downloaded=true;next(task);}});
            }catch(Exception error){main.post(()->{if(active==task&&!task.cancelled){stop();listener.state(id,"failed",error.getMessage()==null?"Speech provider failed":error.getMessage(),true);}});}
        });
    }
    private File fetch(Task task,String text,String requestId)throws Exception {
        VoiceProviders.Selection s=task.selection;task.requestId=requestId;VoiceProviders.Provider p=s.provider;
        if(s.legacy)p=new VoiceProviders.Provider(p.id,p.name,p.url.substring(0,p.url.length()-3),p.key,p.cancel);
        HttpURLConnection c=VoiceProviders.open(p,"POST",s.legacy?"/synthesize/"+requestId:"/audio/speech");task.connection=c;File file=null;
        try {
            c.setReadTimeout(610000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("X-Request-ID",requestId);
            JSONObject body=s.legacy?new JSONObject().put("text",text):new JSONObject().put("model",s.model).put("voice",s.voice).put("input",text).put("response_format","wav").put("speed",1);
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);
            if(task.cancelled)throw new IOException("Cancelled");try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            int status=c.getResponseCode();if(status!=200)throw new IOException("语音服务返回 HTTP "+status+"（"+p.name+"）");
            String type=c.getContentType();if(type==null||!(type.startsWith("audio/")||type.startsWith("application/octet-stream")))throw new IOException("语音服务未返回音频");
            file=File.createTempFile("character-voice-",".wav",context.getCacheDir());
            try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(file)){
                byte[] block=new byte[16384];int n,total=0;
                while((n=in.read(block))!=-1){if(task.cancelled)throw new IOException("Cancelled");total+=n;if(total>40*1024*1024)throw new IOException("Speech waveform exceeds limit");out.write(block,0,n);}
            }
            if(file.length()<44)throw new IOException("语音文件为空");File result=file;file=null;return result;
        }finally{c.disconnect();task.connection=null;if(file!=null)file.delete();}
    }
    private void next(Task task){
        if(active!=task||task.cancelled||player!=null)return;
        if(task.ready.isEmpty()){
            if(task.downloaded){active=null;task.listener.state(task.id,"succeeded","Provider playback completed",true);}return;
        }
        task.playing=task.ready.remove();
        try {
            MediaPlayer p=new MediaPlayer();player=p;p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            p.setDataSource(task.playing.getAbsolutePath());
            p.setOnCompletionListener(done->{if(active!=task)return;done.release();player=null;task.playing.delete();task.playing=null;task.slots.release();next(task);});
            p.setOnErrorListener((failed,what,extra)->{if(active==task){stop();task.listener.state(task.id,"failed","Speech audio playback failed",true);}return true;});
            p.setOnPreparedListener(ready->{if(active==task&&!task.cancelled){try{ready.setPlaybackParams(new PlaybackParams().setSpeed(task.rate).setPitch(task.pitch));ready.start();task.listener.state(task.id,"running","Provider playback started",false);}catch(Exception e){stop();task.listener.state(task.id,"failed","Speech playback could not start",true);}}});p.prepareAsync();
        }catch(Exception e){stop();task.listener.state(task.id,"failed","Speech audio preparation failed",true);}
    }
    boolean playing(){try{return player!=null&&player.isPlaying();}catch(IllegalStateException e){return false;}}
    void stop(){
        Task task=active;if(task==null)return;task.cancelled=true;active=null;task.slots.release(3);
        if(task.connection!=null)task.connection.disconnect();
        if(player!=null){player.release();player=null;}if(task.playing!=null)task.playing.delete();for(File f:task.ready)f.delete();task.ready.clear();
        final String requestId=task.requestId;
        if(!requestId.isEmpty()&&(task.selection.legacy||task.selection.provider.cancel))cancellation.execute(()->{
            HttpURLConnection c=null;try{VoiceProviders.Provider p=task.selection.provider;
                if(task.selection.legacy)p=new VoiceProviders.Provider(p.id,p.name,p.url.substring(0,p.url.length()-3),p.key,true);
                c=VoiceProviders.open(p,"DELETE",task.selection.legacy?"/synthesize/"+requestId:"/audio/speech/"+requestId);c.setReadTimeout(5000);c.getResponseCode();
            }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}});
    }
    void close(){closed=true;stop();worker.shutdown();cancellation.shutdown();}
}
