package local.sliverwolf.assistant;

import android.app.*;
import android.content.*;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.*;

/** Explicitly enabled background voice loop; never launches an Activity or creates a session. */
public final class WakeVoiceService extends Service {
    private static WakeVoiceService current;
    static boolean foreground;
    private final Handler main=new Handler(Looper.getMainLooper());
    private WakeDetector detector;
    private VoiceInput input;
    private DeviceBridge bridge;
    private SessionController session;
    private String name="",request="",voiceSession="",mode="paused",status="初始化语音待命";
    private long nextWake;
    private boolean closed,profileLoaded;
    private int hits,recognized,replies,ownerRejected,lastOwnerSpeechSamples;
    private String lastOwnerRejection="none";
    static boolean running(){return current!=null;}
    static org.json.JSONObject diagnostics(){
        WakeVoiceService s=current;
        return s==null?DshTransport.object("state","disabled"):DshTransport.object("state",s.mode,"status",s.status,"hits",s.hits,"ownerRejected",s.ownerRejected,"recognized",s.recognized,"replies",s.replies,
            "lastOwnerRejection",s.lastOwnerRejection,"lastOwnerSpeechSamples",s.lastOwnerSpeechSamples,
            "learnedSamples",s.detector==null?0:s.detector.learnedSamples,"learningStatus",s.detector==null?"idle":s.detector.learningStatus,"ownerFitMillis",s.detector==null?0:s.detector.ownerFitMillis,"ownerProfile",s.detector==null?"none":s.detector.ownerProfile,"samplesRead",s.detector==null?0:s.detector.samplesRead,"peakRms",s.detector==null?0:s.detector.peakRms);
    }
    static boolean microphoneBusy(){return current!=null&&((current.detector!=null&&current.detector.busy())||(current.input!=null&&current.input.active()));}
    static String phase(){return current==null?"disabled":current.mode;}
    static String status(){return current==null?"自动唤醒未开启":current.status;}
    static void activity(boolean visible){foreground=visible;if(visible&&current!=null)current.pause("主界面使用手动语音");}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate() {
        super.onCreate();current=this;
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("wake-voice","语音待命",NotificationManager.IMPORTANCE_LOW));
        publish();bridge=SharedDeviceBridge.acquire(this);detector=new WakeDetector(this);
        input=new VoiceInput(this,new VoiceInput.Listener(){
            public void ready(){
                if(closed||foreground||!"listening".equals(mode))return;
                ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_MUSIC,45);tone.startTone(ToneGenerator.TONE_PROP_BEEP,100);
                main.postDelayed(tone::release,180);
            }
            public void state(String value){
                status=value;publish();
                if(!input.active()&&"listening".equals(mode)){mode="paused";nextWake=SystemClock.elapsedRealtime()+2000;}
            }
            public void result(String text){
                recognized++;
                if(foreground||!voiceSession.equals(session.selected)||!session.connected){pause("会话或连接已变化，未发送");return;}
                String id=java.util.UUID.randomUUID().toString();request=id;mode="waiting";
                if(!session.send(text,id)){request="";pause("无法发送，请查看文字记录");return;}
                detector.confirmLearning();status="等待当前会话回复";publish();
            }
        });
        session=new SessionController(getSharedPreferences("session",0),()->{});session.connect();main.post(tick);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null&&"disable".equals(intent.getAction()))getSharedPreferences("wake",0).edit().putBoolean("enabled",false).apply();
        if(!getSharedPreferences("wake",0).getBoolean("enabled",false)||!new OwnerVoiceStore(this).exists()){stopSelf();return START_NOT_STICKY;}
        return START_NOT_STICKY;
    }
    private void publish() {
        PendingIntent stop=PendingIntent.getService(this,49,new Intent(this,WakeVoiceService.class).setAction("disable"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(49,new Notification.Builder(this,"wake-voice").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("LumenDSH · 语音待命").setContentText(status).setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this,49,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(new Notification.Action.Builder(null,"停止待命",stop).build()).build());
    }
    private void pause(String reason) {
        if(detector!=null)detector.stop();if(input!=null)input.stop();
        if(bridge!=null&&!"paused".equals(mode))bridge.stopSpeaking();
        request="";mode="paused";nextWake=SystemClock.elapsedRealtime()+1500;
        if(!status.equals(reason)){status=reason;publish();}
    }
    private final Runnable tick=new Runnable(){public void run(){if(closed)return;try{advance();}catch(Exception|LinkageError error){fail(error.getMessage());}if(!closed)main.postDelayed(this,300);}};
    private void advance()throws Exception {
        if("failed".equals(mode)||foreground)return;
        if(!new OwnerVoiceStore(this).exists()){fail("请先录入主人声纹");return;}
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){fail("麦克风权限未授权");return;}
        if(getSystemService(AudioManager.class).getMode()!=AudioManager.MODE_NORMAL){pause("通话期间暂停语音");return;}
        String selected=getSharedPreferences("session",0).getString("session","");
        if(!selected.equals(session.selected)){pause("会话切换，重新待命");session.select(selected);return;}
        String configured=PresentationSettings.load(this).name;
        if(!configured.equals(name)){pause("正在更新唤醒词");name=configured;return;}
        if(!session.connected||selected.isEmpty()){pause("等待本机会话连接");return;}
        if(!profileLoaded) {
            if(!bridge.profileReady())return;
            PetStore pets=new PetStore(new java.io.File(getFilesDir(),"appearances"));String key=pets.activeProfile();
            if(!key.isEmpty()) {
                AssistantProfile profile=new AssistantProfile.Store(new java.io.File(getFilesDir(),"assistant-profiles")).load(key);
                bridge.beginProfile(profile.voice);bridge.finishProfile(profile.voice);
            }
            profileLoaded=true;
        }
        if(!request.isEmpty()) {
            VoiceReply reply=VoiceReply.find(session.conversation,request);
            if(reply!=null){String key="voice-"+request;request="";mode="speaking";
                if(!reply.text.isEmpty()){bridge.speakReply(key,reply.text);replies++;status="正在回复";}
                else status=reply.notice;
                publish();nextWake=SystemClock.elapsedRealtime()+1500;
            }
            return;
        }
        if("listening".equals(mode))return;
        if(bridge.isSpeaking())return;
        if("speaking".equals(mode)){mode="paused";nextWake=SystemClock.elapsedRealtime()+1500;return;}
        if("standby".equals(mode)){if(session.running||!session.decisions.isEmpty()||!session.canSend())pause("任务执行中，等待下一次唤醒");return;}
        if(SystemClock.elapsedRealtime()<nextWake||detector.busy()||session.running||!session.canSend()||!session.decisions.isEmpty()
            ||!getSharedPreferences("session",0).getString("pendingId","").isEmpty())return;
        mode="standby";
        detector.start(name,new WakeDetector.Listener(){
            public void ready(String phrase){status="待命 · 请说“"+phrase+"”";publish();}
            public void failed(String reason){fail(reason);}
            public void rejected(String reason,int speechSamples){ownerRejected++;lastOwnerRejection=reason;lastOwnerSpeechSamples=speechSamples;}
            public void detected(){
                hits++;
                if(closed||foreground){pause("主界面使用手动语音");return;}
                if(session.running||!session.canSend()||!session.decisions.isEmpty()){pause("任务执行中，暂不接收新指令");return;}
                mode="listening";voiceSession=session.selected;
                try{input.start(bridge.voiceProfile().language);}catch(Exception error){fail(error.getMessage());}
            }
        });
    }
    private void fail(String reason){pause("自动唤醒暂停："+reason);mode="failed";}
    @Override public void onDestroy(){closed=true;main.removeCallbacksAndMessages(null);pause("语音待命已停止");
        if(detector!=null)detector.close();if(session!=null)session.destroy();
        if(current==this)current=null;if(bridge!=null)SharedDeviceBridge.release();stopForeground(true);super.onDestroy();}
}
