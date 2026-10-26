package local.sliverwolf.web;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

/** One enrollment action; fitting and backend selection are internal operations. */
public final class OwnerVoiceActivity extends Activity {
    private TextView status;
    private Button enroll;
    private OwnerEnrollment job;
    private boolean confirming;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        WakeVoiceService.activity(true);
        SharedDeviceBridge.acquire(this).stopSpeaking();
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(40,60,40,40);
        TextView title=new TextView(this);title.setText("主人声纹");title.setTextSize(24);layout.addView(title);
        status=new TextView(this);status.setTextSize(17);layout.addView(status,new LinearLayout.LayoutParams(-1,0,1));
        status.setText(new OwnerVoiceStore(this).exists()?"已保存主人声纹，无需重复录入。\n\n系统会自动使用现有样本完成个人适配。只有需要更换声纹时，才点击下方按钮。":"请录入主人声纹。系统会自动完成个人适配。\n\n按提示自然说话，录音只在本机内存中处理。");
        enroll=new Button(this);enroll.setText("录入声纹");layout.addView(enroll);enroll.setOnClickListener(v->authorize());setContentView(layout);
    }
    private void authorize() {
        if(job!=null||confirming||OwnerEnrollment.active)return;
        KeyguardManager keyguard=getSystemService(KeyguardManager.class);
        if(!keyguard.isDeviceSecure()){status.setText("请先在系统中设置锁屏密码。");return;}
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},1);return;}
        Intent intent=keyguard.createConfirmDeviceCredentialIntent("主人声纹","验证手机身份后录入声纹");
        if(intent==null){status.setText("系统身份确认不可用。");return;}confirming=true;startActivityForResult(intent,2);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(request!=2)return;boolean requested=confirming;confirming=false;
        if(!requested||result!=RESULT_OK){status.setText("录入已取消，原声纹未改变。");return;}
        String name;try{name=PresentationSettings.load(this).name;}catch(Exception error){status.setText(error.getMessage());return;}
        enroll.setEnabled(false);status.setText("准备录入，请等待朗读提示…");
        job=new OwnerEnrollment(this);
        job.start(name,true,new OwnerEnrollment.Listener(){
            public void progress(String message){status.setText(message);}
            public void complete(float[][] vectors){
                try {
                    OwnerVoiceStore.wake(OwnerVoiceActivity.this,"").remove();
                    new OwnerVoiceStore(OwnerVoiceActivity.this).save(vectors);
                    status.setText("声纹已保存，个人适配完成。");
                }catch(Exception error){status.setText("保存失败："+error.getMessage());}
                finishJob();
            }
            public void failed(String reason){status.setText("原声纹未改变：\n"+reason);finishJob();}
        });
    }
    private void finishJob(){job=null;enroll.setEnabled(true);}
    @Override protected void onResume(){super.onResume();WakeVoiceService.activity(true);CompanionOverlayService.activity(true);}
    @Override protected void onPause(){
        if(job!=null){job.cancel();finishJob();status.setText("录入已取消，原声纹未改变。");}
        WakeVoiceService.activity(false);CompanionOverlayService.activity(false);super.onPause();
    }
    @Override protected void onDestroy(){if(job!=null)job.cancel();SharedDeviceBridge.release();super.onDestroy();}
}
