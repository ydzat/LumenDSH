package local.sliverwolf.assistant;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local synthesis transport; DeviceSpeech remains the sole audio-focus owner. */
final class CharacterSpeech {
    static final String ENGINE="lumendsh.silver_wolf.local";
    interface Listener { void state(String id,String state,String detail,boolean terminal); }
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService cancellation=Executors.newSingleThreadExecutor();
    private Task active;
    private MediaPlayer player;
    private boolean closed;
    private static final class Task {
        final String id,token; volatile boolean cancelled; volatile HttpURLConnection connection; File audio;
        Task(String id,String token){this.id=id;this.token=token;}
    }
    CharacterSpeech(Context context){this.context=context;}
    boolean configured(){return !token().isEmpty();}
    private String token(){return context.getSharedPreferences("character-voice",0).getString("token","");}
    void pair(String token){
        if(!token.matches("[A-Za-z0-9_-]{40,100}"))throw new IllegalArgumentException("Invalid voice pairing");
        if(!context.getSharedPreferences("character-voice",0).edit().putString("token",token).commit())
            throw new IllegalStateException("Cannot save voice pairing");
    }
    private static HttpURLConnection connection(String method,Task task) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:3086/synthesize/"+task.id).openConnection();
        c.setRequestMethod(method);c.setConnectTimeout(5000);c.setReadTimeout(method.equals("DELETE")?5000:610000);
        c.setInstanceFollowRedirects(false);c.setRequestProperty("Authorization","Bearer "+task.token);
        return c;
    }
    void speak(String id,String text,float rate,float pitch,Listener listener){
        if(closed||active!=null||!configured())throw new IllegalStateException("Character voice unavailable or busy");
        Task task=new Task(id,token());active=task;
        worker.execute(()->{
            File file=null;
            try {
                if(task.cancelled)return;
                HttpURLConnection c=connection("POST",task);task.connection=c;
                c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
                byte[] body=new JSONObject().put("text",text).toString().getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(body.length);
                if(task.cancelled)return;
                try(OutputStream out=c.getOutputStream()){out.write(body);}
                if(c.getResponseCode()!=200){
                    String detail="Character synthesis unavailable (HTTP "+c.getResponseCode()+")";
                    try(InputStream in=c.getErrorStream()){
                        if(in!=null){byte[] bytes=new byte[2048];int n=in.read(bytes);if(n>0)detail=new JSONObject(new String(bytes,0,n,StandardCharsets.UTF_8)).optString("error",detail);}
                    }
                    throw new IOException(detail);
                }
                if(!"audio/wav".equals(c.getContentType()))throw new IOException("Unexpected synthesis response");
                file=File.createTempFile("character-voice-",".wav",context.getCacheDir());
                try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(file)){
                    byte[] bytes=new byte[16384];int n,total=0;
                    while((n=in.read(bytes))!=-1){if(task.cancelled)throw new IOException("Cancelled");total+=n;
                        if(total>40*1024*1024)throw new IOException("Speech waveform exceeds limit");out.write(bytes,0,n);}
                }
                final File ready=file;file=null;
                main.post(()->{
                    if(closed||task.cancelled||active!=task){ready.delete();return;}
                    task.audio=ready;
                    try {
                        player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                        player.setDataSource(ready.getAbsolutePath());
                        player.setOnCompletionListener(p->{if(active==task){release();listener.state(id,"succeeded","Character voice playback completed",true);}});
                        player.setOnErrorListener((p,what,extra)->{if(active==task){release();listener.state(id,"failed","Character audio playback failed",true);}return true;});
                        player.setOnPreparedListener(p->{if(active==task&&!task.cancelled){
                            try {p.setPlaybackParams(new android.media.PlaybackParams().setSpeed(rate).setPitch(pitch));p.start();
                                listener.state(id,"running","Character playback started",false);
                            }catch(Exception error){release();listener.state(id,"failed","Character playback could not start",true);}
                        }});
                        player.prepareAsync();
                    }catch(Exception error){release();listener.state(id,"failed","Character audio preparation failed",true);}
                });
            }catch(Exception error){main.post(()->{
                if(active==task&&!task.cancelled){release();listener.state(id,"failed",error.getMessage()==null?"Character synthesis failed":error.getMessage(),true);}
            });}finally{if(task.connection!=null)task.connection.disconnect();if(file!=null)file.delete();}
        });
    }
    boolean playing(){try{return player!=null&&player.isPlaying();}catch(IllegalStateException error){return false;}}
    void stop(){
        Task task=active;if(task==null)return;task.cancelled=true;
        if(task.connection!=null)task.connection.disconnect();release();
        cancellation.execute(()->{HttpURLConnection c=null;try{c=connection("DELETE",task);c.getResponseCode();}catch(Exception ignored){}finally{if(c!=null)c.disconnect();}});
    }
    private void release(){if(player!=null){player.release();player=null;}if(active!=null&&active.audio!=null)active.audio.delete();active=null;}
    void close(){closed=true;stop();worker.shutdown();cancellation.shutdown();}
}
