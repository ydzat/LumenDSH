package local.sliverwolf.assistant;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Window/drag/dock/visibility policies adapted from OverlayService a5af7b1, MIT (c) 2026 zhou-gu24. */
public final class CompanionOverlayService extends Service {
    static boolean activityVisible;
    private static java.lang.ref.WeakReference<CompanionOverlayService> current=new java.lang.ref.WeakReference<>(null);
    private final Handler main=new Handler();
    private final java.util.concurrent.ExecutorService images=java.util.concurrent.Executors.newSingleThreadExecutor();
    private WindowManager windows;
    private WindowManager.LayoutParams position;
    private LinearLayout panel;
    private View viewportProbe;
    private WindowManager.LayoutParams probePosition;
    private boolean probeAttached;
    private Avatar avatar;
    private TextView state;
    private SessionController observer;
    private final HabitatState phase=new HabitatState();
    private SharedPreferences prefs;
    private PetSprite sprite;
    private boolean attached,hidden,closed,loading,appearanceError;
    private int imageGeneration;
    private String spriteState="idle",presentationJson="";
    private PresentationSettings presentation;
    private final Rect available=new Rect();
    private boolean fitting;
    static void activity(boolean visible){activityVisible=visible;CompanionOverlayService service=current.get();if(service!=null)service.refreshVisibility();}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate() {
        super.onCreate();current=new java.lang.ref.WeakReference<>(this);
        prefs=getSharedPreferences("overlay",0);hidden=prefs.getBoolean("hidden",false);
        windows=getSystemService(WindowManager.class);
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("companion-overlay","悬浮角色",NotificationManager.IMPORTANCE_LOW));
        startForeground(48,new Notification.Builder(this,"companion-overlay").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("LumenDSH · 悬浮角色").setContentText("角色与任务状态；语音待命在设置中开启")
            .setContentIntent(PendingIntent.getActivity(this,48,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(new Notification.Action.Builder(null,"召回",action("recall")).build())
            .addAction(new Notification.Action.Builder(null,"隐藏",action("hide")).build())
            .addAction(new Notification.Action.Builder(null,"关闭",action("disable")).build()).setOngoing(true).build());
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
        background.setColor(0xe6111725);background.setCornerRadius(dp(20));background.setStroke(dp(1),0xff70608d);panel.setBackground(background);
        avatar=new Avatar();panel.addView(avatar,new LinearLayout.LayoutParams(dp(112),dp(122)));
        state=new TextView(this);state.setTextColor(0xffd5d8e6);state.setTextSize(11);state.setGravity(Gravity.CENTER);state.setMaxLines(2);
        panel.addView(state,new LinearLayout.LayoutParams(-1,dp(38)));
        position=new WindowManager.LayoutParams(dp(112),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                |WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        position.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        // A stationary, transparent, untouchable strip observes full-height IME insets.
        // Small moving windows report only their own clipped portion on Android 10.
        viewportProbe=new View(this);
        probePosition=new WindowManager.LayoutParams(1,WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,position.flags|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT);
        probePosition.gravity=Gravity.TOP|Gravity.LEFT;
        probePosition.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        position.gravity=Gravity.TOP|Gravity.START;position.x=prefs.getInt("x",dp(8));position.y=prefs.getInt("y",dp(120));
        panel.getViewTreeObserver().addOnGlobalLayoutListener(this::fitAvailableFrame);
        observer=new SessionController(getSharedPreferences("session",0),this::changed);
        observer.connect();main.post(tick);
    }
    private PendingIntent action(String value){return PendingIntent.getService(this,48,new Intent(this,CompanionOverlayService.class).setAction(value),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null&&"disable".equals(intent.getAction()))prefs.edit().putBoolean("enabled",false).apply();
        if(!prefs.getBoolean("enabled",false)||!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
        if(intent!=null&&"recall".equals(intent.getAction())){hidden=false;prefs.edit().putBoolean("hidden",false).apply();}
        if(intent!=null&&"hide".equals(intent.getAction())){hidden=true;prefs.edit().putBoolean("hidden",true).apply();}
        refreshVisibility();return START_STICKY;
    }
    private final Runnable tick=new Runnable(){public void run(){if(closed)return;refreshVisibility();if(attached){fitAvailableFrame();changed();}main.postDelayed(this,500);}};
    private void changed() {
        if(observer==null||state==null)return;
        String configured=PresentationSettings.stored(this);
        if(!configured.equals(presentationJson))try{
            presentation=PresentationSettings.parse(configured);presentationJson=configured;
            state.setTextColor(presentation.textColor);
            android.graphics.drawable.GradientDrawable style=new android.graphics.drawable.GradientDrawable();
            style.setColor(presentation.backgroundColor);style.setCornerRadius(dp(20));style.setStroke(dp(1),0xff70608d);panel.setBackground(style);
            int width=dp(112*presentation.size);position.width=width;
            avatar.setLayoutParams(new LinearLayout.LayoutParams(width,dp(122*presentation.size)));
            clamp();if(attached)windows.updateViewLayout(panel,position);
            if(sprite!=null)sprite.playbackSpeed=presentation.animationSpeed;
        }catch(Exception error){prefs.edit().putString("error","Invalid presentation settings").apply();}
        String selected=getSharedPreferences("session",0).getString("session","");
        if(!selected.equals(observer.selected)){phase.reset();observer.select(selected);}
        HabitatFeed feed=HabitatFeed.from(observer.conversation);long now=SystemClock.elapsedRealtime();
        phase.accept(feed.phase,feed.sequence,now);
        spriteState=phase.visible(observer.connected,!observer.decisions.isEmpty(),false,false,now);
        String wake=WakeVoiceService.status();
        if(!appearanceError&&!selected.isEmpty()&&observer.connected&&observer.decisions.isEmpty()&&getSharedPreferences("wake",0).getBoolean("enabled",false)){
            state.setText(wake);String voice=WakeVoiceService.phase();
            if("listening".equals(voice))spriteState="listening";else if("speaking".equals(voice))spriteState="speaking";
            if(attached)avatar.invalidate();return;
        }
        state.setText(appearanceError?"形象载入失败":selected.isEmpty()?"尚未选择会话":!observer.connected?"连接中断 · 状态未知":!observer.decisions.isEmpty()?"等待你的确认":feed.title);
        if(attached)avatar.invalidate();
    }
    private void refreshVisibility() {
        if(closed||panel==null)return;
        if(!Settings.canDrawOverlays(this)){prefs.edit().putBoolean("enabled",false).putString("error","悬浮窗权限不可用").apply();stopSelf();return;}
        boolean show=prefs.getBoolean("enabled",false)&&!activityVisible&&!hidden
            &&getSystemService(PowerManager.class).isInteractive()&&!getSystemService(KeyguardManager.class).isKeyguardLocked();
        if(show&&!attached) {
            try{available.setEmpty();windows.addView(viewportProbe,probePosition);probeAttached=true;clamp();windows.addView(panel,position);attached=true;loadSprite();}
            catch(RuntimeException error){prefs.edit().putString("error","Window unavailable: "+error.getClass().getSimpleName()).apply();stopSelf();}
        }else if(!show&&attached){windows.removeView(panel);attached=false;if(probeAttached){windows.removeView(viewportProbe);probeAttached=false;}if(sprite!=null)sprite.pause();}
    }
    private void loadSprite() {
        if(loading)return;loading=true;final int generation=++imageGeneration;
        images.execute(()->{
            try {
                PetStore.Pack pack=new PetStore(new java.io.File(getFilesDir(),"appearances")).restore();
                PetSprite loaded=pack==null?DefaultPet.load(getAssets()):pack.sprite;
                main.post(()->{loading=false;if(closed||generation!=imageGeneration){loaded.bitmap.recycle();return;}
                    appearanceError=false;if(presentation!=null)loaded.playbackSpeed=presentation.animationSpeed;PetSprite previous=sprite;sprite=loaded;if(previous!=null)previous.bitmap.recycle();avatar.invalidate();});
            }catch(Exception error){main.post(()->{loading=false;if(closed)return;appearanceError=true;prefs.edit().putString("error","Appearance unavailable: "+error.getClass().getSimpleName()).apply();state.setText("形象载入失败");});}
        });
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void fitAvailableFrame() {
        if(!attached||fitting)return;
        Rect frame=new Rect();viewportProbe.getWindowVisibleDisplayFrame(frame);
        WindowInsets insets=viewportProbe.getRootWindowInsets();
        Point screen=new Point();windows.getDefaultDisplay().getRealSize(screen);
        if(insets!=null&&insets.getDisplayCutout()!=null){DisplayCutout cutout=insets.getDisplayCutout();
            frame.left=Math.max(frame.left,cutout.getSafeInsetLeft());frame.top=Math.max(frame.top,cutout.getSafeInsetTop());
            frame.right=Math.min(frame.right,screen.x-cutout.getSafeInsetRight());frame.bottom=Math.min(frame.bottom,screen.y-cutout.getSafeInsetBottom());}
        if(frame.isEmpty()||frame.equals(available))return;
        available.set(frame);fitting=true;
        position.x=prefs.getInt("x",dp(8));position.y=prefs.getInt("y",dp(120));
        clamp();windows.updateViewLayout(panel,position);fitting=false;
    }
    private void clamp() {
        Point bounds=new Point();windows.getDefaultDisplay().getRealSize(bounds);
        Rect area=available.isEmpty()?new Rect(0,dp(24),bounds.x,bounds.y):available;
        int width=Math.max(1,position.width),height=Math.max(dp(160*(presentation==null?1:presentation.size)),panel.getMeasuredHeight());
        position.x=Math.max(area.left+dp(4),Math.min(position.x,Math.max(area.left+dp(4),area.right-width-dp(4))));
        position.y=Math.max(area.top+dp(4),Math.min(position.y,Math.max(area.top+dp(4),area.bottom-height-dp(4))));
    }
    private void dock(float rawX,float rawY) {
        Point bounds=new Point();windows.getDefaultDisplay().getSize(bounds);
        float left=rawX,right=bounds.x-rawX,top=rawY;
        if(top<left&&top<right)position.y=dp(24);else position.x=left<right?dp(4):bounds.x-position.width-dp(4);
        clamp();if(attached)windows.updateViewLayout(panel,position);
        prefs.edit().putInt("x",position.x).putInt("y",position.y).apply();
    }
    @Override public void onConfigurationChanged(Configuration next){super.onConfigurationChanged(next);available.setEmpty();clamp();if(attached){windows.updateViewLayout(panel,position);panel.requestLayout();}}
    private final class Avatar extends View {
        private float downX,downY,tilt;private int x,y;private boolean moved;
        Avatar(){super(CompanionOverlayService.this);setContentDescription("悬浮角色与任务状态，可拖动贴边，点击回到主界面");}
        @Override protected void onDraw(Canvas canvas){
            boolean animate=android.animation.ValueAnimator.areAnimatorsEnabled()&&attached;
            if(sprite!=null){canvas.save();canvas.rotate(animate?tilt:0,getWidth()/2f,getHeight()/2f);
                sprite.draw(canvas,getWidth()/14f,getHeight()/30.5f,getWidth()*6f/7,getHeight()*104f/122,"streaming".equals(spriteState)||"done".equals(spriteState)?"speaking":spriteState,SystemClock.elapsedRealtime(),animate);canvas.restore();}
            if(animate)postInvalidateDelayed(80);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            switch(event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:downX=event.getRawX();downY=event.getRawY();x=position.x;y=position.y;moved=false;return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                    if(Math.abs(dx)+Math.abs(dy)>dp(8)){moved=true;position.x=x+(int)dx;position.y=y+(int)dy;tilt=Math.max(-8,Math.min(8,dx/12));clamp();if(attached)windows.updateViewLayout(panel,position);}return true;
                case MotionEvent.ACTION_UP:tilt=0;if(moved)dock(event.getRawX(),event.getRawY());else performClick();return true;
                case MotionEvent.ACTION_CANCEL:tilt=0;return true;
            }return super.onTouchEvent(event);
        }
        @Override public boolean performClick(){super.performClick();startActivity(new Intent(CompanionOverlayService.this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));return true;}
        @Override protected void onDetachedFromWindow(){super.onDetachedFromWindow();}
    }
    @Override public void onDestroy(){closed=true;main.removeCallbacksAndMessages(null);if(attached)windows.removeView(panel);attached=false;
        if(probeAttached){windows.removeView(viewportProbe);probeAttached=false;}
        if(observer!=null)observer.destroy();images.shutdown();if(sprite!=null)sprite.bitmap.recycle();
        if(current.get()==this)current.clear();stopForeground(true);super.onDestroy();}
}
