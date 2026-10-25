package local.sliverwolf.assistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Native habitat presentation; interaction controls remain accessible Android widgets. */
final class HabitatView extends LinearLayout {
    interface Actions { void voice(); void stop(); void transcript(); void decisions(); void settings(); void touch(String action); }
    private final Scene scene;
    private final HabitatState state=new HabitatState();
    private final TextView connection, feedback;
    private TextView name;
    private PresentationSettings presentation;
    private final ReplyCard bubble;
    private final android.widget.ImageButton voice;
    private final Button pending;
    private long lastActivity;
    private boolean narrationVisible, reading;
    private String conversationText="", contextText="";
    private final Runnable fadeNarration=()->{
        if(!reading&&android.os.SystemClock.uptimeMillis()-lastActivity>=holdMillis())setNarrationVisible(false);
        else scheduleFade();
    };
    private final FrameLayout stage, narration, layers;
    private final View narrationSpace;
    private final LinearLayout body, controls, interaction;

    HabitatView(Context context, Actions actions) {
        super(context);
        setOrientation(VERTICAL); setBackgroundColor(Color.rgb(12,20,32));
        setPadding(dp(20), dp(12), dp(20), dp(8));
        LinearLayout header = new LinearLayout(context);
        name = text("银狼", 17); name.setLetterSpacing(.16f);
        header.addView(name, new LinearLayout.LayoutParams(0,-2,1));
        header.addView(iconControl(context,"ic_menu","菜单",actions::settings),new LinearLayout.LayoutParams(dp(48),dp(48)));addView(header);
        connection = text("正在连接本机 DSH", 9); connection.setTextColor(0xff8fa3b9); addView(connection);
        body=new LinearLayout(context);body.setOrientation(VERTICAL);
        layers=new FrameLayout(context);
        layers.addView(body,new FrameLayout.LayoutParams(-1,-1));
        addView(layers,new LinearLayout.LayoutParams(-1,0,1));
        controls=new LinearLayout(context);controls.setOrientation(VERTICAL);
        stage = new FrameLayout(context);
        scene = new Scene(context); stage.addView(scene, new FrameLayout.LayoutParams(-1,-1));
        bubble = new ReplyCard(context,new ReplyCard.Listener(){
            public void activity(boolean touching){reading=touching;scene.reading=touching;revealNarration();}
            public void expand(){actions.transcript();}
        });
        narration=new FrameLayout(context);
        interaction=new LinearLayout(context);interaction.setOrientation(VERTICAL);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1,-1, Gravity.TOP);
        bp.setMargins(dp(12),0,dp(12),0); narration.addView(bubble,bp);
        narration.setContentDescription("会话记录，轻触显示");
        narration.setOnClickListener(v->revealNarration());
        bubble.setVisibility(INVISIBLE);bubble.setAlpha(0);
        body.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        narrationSpace=new View(context);
        narrationSpace.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        interaction.addView(narrationSpace,new LinearLayout.LayoutParams(-1,dp(140)));
        interaction.addView(controls,new LinearLayout.LayoutParams(-1,-2));
        body.addView(interaction,new LinearLayout.LayoutParams(-1,-2));
        // Draw conversation above the complete scene without resizing the character stage.
        narration.setElevation(dp(2));layers.addView(narration,new FrameLayout.LayoutParams(-1,dp(140)));
        layers.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->positionNarration());
        controls.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->positionNarration());
        pending = quietControl("", () -> { actions.stop(); actions.decisions(); }); controls.addView(pending);
        feedback = text("我在。",11); feedback.setGravity(Gravity.CENTER); feedback.setMaxLines(3); controls.addView(feedback);
        controls.addView(new VoiceBars(context),new LinearLayout.LayoutParams(-1,dp(24)));
        voice = iconControl(context,"ic_mic","开始说话",()->{revealNarration();actions.voice();});
        voice.setColorFilter(0xffc0e1df);voice.setPadding(dp(17),dp(17),dp(17),dp(17));
        GradientDrawable voiceShape=new GradientDrawable();voiceShape.setColor(0xff192d38);
        voiceShape.setCornerRadius(dp(32));voiceShape.setStroke(dp(1),0xff39545f);
        voice.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x336ed3c4),voiceShape,null));
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(dp(64),dp(64));
        vp.gravity=Gravity.CENTER_HORIZONTAL;vp.setMargins(0,dp(6),0,dp(8));controls.addView(voice,vp);
        float savedZoom=context.getSharedPreferences("appearance",0).getFloat("zoom",
            context.getSharedPreferences("appearance",0).getBoolean("close",false)?1:0);
        scene.zoom=Math.max(0,Math.min(1,savedZoom));scene.closeness=scene.zoom;
        scene.touchAction=actions::touch;
        scene.setContentDescription("角色，单击触摸，双指缩放");
        try{configure(PresentationSettings.load(context));}catch(Exception e){
            new android.app.AlertDialog.Builder(context).setTitle("展示配置加载失败").setMessage(e.getMessage()).setPositiveButton("返回",null).show();
        }
    }
    void configure(PresentationSettings settings) {
        presentation=settings;if(scene.pet!=null)scene.pet.playbackSpeed=settings.animationSpeed;name.setText(settings.name);scene.setContentDescription(settings.name+"，单击触摸，双指缩放");
        bubble.colors(settings.backgroundColor,settings.textColor);scene.invalidate();
    }
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight) {
        super.onSizeChanged(width,height,oldWidth,oldHeight);
        if(body==null)return;
        boolean wide=width>height;
        body.setOrientation(wide?HORIZONTAL:VERTICAL);
        stage.setLayoutParams(new LinearLayout.LayoutParams(wide?0:-1,wide?-1:0,1));
        interaction.setLayoutParams(new LinearLayout.LayoutParams(wide?0:-1,wide?-1:-2,wide?1:0));
        narrationSpace.setLayoutParams(new LinearLayout.LayoutParams(-1,wide?0:dp(140),wide?1:0));
        controls.setGravity(wide?Gravity.CENTER:Gravity.CENTER_HORIZONTAL);
    }
    private float characterBottom;
    private boolean placementQueued;
    private void positionNarration(){
        if(placementQueued||layers==null)return;placementQueued=true;
        layers.post(()->{
            placementQueued=false;if(controls.getHeight()==0||stage.getHeight()==0)return;
            android.graphics.Rect available=new android.graphics.Rect(0,0,narrationSpace.getWidth(),narrationSpace.getHeight());
            layers.offsetDescendantRectToMyCoords(narrationSpace,available);
            if(body.getOrientation()==VERTICAL&&characterBottom>0){
                android.graphics.Rect stageRect=new android.graphics.Rect(0,0,stage.getWidth(),stage.getHeight());
                layers.offsetDescendantRectToMyCoords(stage,stageRect);
                // Expand upward through unused stage space; keep a small gap below the scaled feet.
                available.top=Math.min(available.top,stageRect.top+Math.round(characterBottom)+dp(10));
            }
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)narration.getLayoutParams();
            if(lp.leftMargin!=available.left||Math.abs(lp.topMargin-available.top)>dp(2)||lp.width!=available.width()||Math.abs(lp.height-available.height())>dp(2)){
                lp.leftMargin=available.left;lp.topMargin=available.top;lp.width=available.width();lp.height=available.height();narration.setLayoutParams(lp);
            }
        });
    }
    void update(boolean connected, boolean listening, boolean speaking, boolean running,
            int decisionCount, String state, String voiceFeedback, String spokenText, HabitatFeed feed) {
        scene.connected=connected; scene.listening=listening; scene.speaking=speaking;
        scene.running=running; scene.taskState=state; scene.reading=reading; scene.decision=decisionCount>0;
        connection.setText(connected ? (presentation==null?"LOCAL":presentation.tag)+" · 本机已连接" : "本机连接中断 · 执行情况未知");
        pending.setVisibility(decisionCount>0 ? VISIBLE : GONE);
        pending.setText("有 " + decisionCount + " 项等待你处理");
        pending.setEnabled(!listening);
        this.state.accept(feed.phase,feed.sequence,android.os.SystemClock.uptimeMillis());
        scene.setFeed(feed);
        feedback.setText(voiceFeedback);
        String voiceAction=listening?"结束说话":speaking?"停止播报":"开始说话";
        voice.setContentDescription(voiceAction);voice.setTooltipText(voiceAction);
        voice.setImageResource(getResources().getIdentifier(listening||speaking?"ic_square":"ic_mic","drawable",getContext().getPackageName()));
        revealNarration();scene.invalidate();
    }
    void setPet(PetSprite pet) { scene.pet=pet;if(pet!=null&&presentation!=null)pet.playbackSpeed=presentation.animationSpeed; scene.invalidate(); }
    void showConversation(String text) {
        if(conversationText.equals(text))return;
        conversationText=text;contextText="";refreshConversation();revealNarration();
    }
    void offerSource(AttentionQueue.Source source,String reference,String text) {
        contextText=source.label+text;refreshConversation();revealNarration();
    }
    private void refreshConversation(){
        bubble.showContent(conversationText+(contextText.isEmpty()?"":"\n\n"+contextText));
    }
    void resetConversation(){
        state.reset();conversationText="";contextText="";refreshConversation();
        scene.setFeed(new HabitatFeed("等待下一次交互","",-1,false));
    }
    private long holdMillis(){return (presentation==null?12:presentation.holdSeconds)*1000L;}
    private void scheduleFade(){removeCallbacks(fadeNarration);if(isShown())postDelayed(fadeNarration,holdMillis());}
    private void revealNarration(){lastActivity=android.os.SystemClock.uptimeMillis();setNarrationVisible(true);scheduleFade();}
    private void setNarrationVisible(boolean visible){
        if(narrationVisible==visible)return;
        narrationVisible=visible;scene.foreground=visible;bubble.animate().cancel();
        if(visible)bubble.setVisibility(VISIBLE);
        bubble.setEnabled(visible);
        if(!android.animation.ValueAnimator.areAnimatorsEnabled()){
            bubble.setAlpha(visible?1:0);bubble.setVisibility(visible?VISIBLE:INVISIBLE);return;
        }
        bubble.animate().alpha(visible?1:0).setDuration(visible?250:600)
            .withEndAction(()->{if(!narrationVisible)bubble.setVisibility(INVISIBLE);}).start();
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();revealNarration();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(fadeNarration);super.onDetachedFromWindow();}
    @Override protected void onVisibilityChanged(View changed,int visibility){
        super.onVisibilityChanged(changed,visibility);if(bubble==null)return;
        if(isShown())revealNarration();else {removeCallbacks(fadeNarration);reading=false;scene.reading=false;}
    }
    static android.widget.ImageButton iconControl(Context context,String icon,String label,Runnable action){
        android.widget.ImageButton button=new android.widget.ImageButton(context);
        button.setImageResource(context.getResources().getIdentifier(icon,"drawable",context.getPackageName()));
        int pad=Math.round(12*context.getResources().getDisplayMetrics().density);
        button.setPadding(pad,pad,pad,pad);button.setContentDescription(label);button.setTooltipText(label);
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x227d99cc),null,new android.graphics.drawable.ColorDrawable(Color.WHITE)));
        button.setOnClickListener(v->action.run());return button;
    }
    private TextView text(String value,int size) {
        TextView t=new TextView(getContext()); t.setText(value); t.setTextSize(size); t.setTextColor(0xffe0e5f0); return t;
    }
    private Button control(String value,Runnable action) {
        Button b=new Button(getContext()); b.setText(value); b.setTextSize(12); b.setAllCaps(false);
        b.setTextColor(0xffc0e1df); b.setMinHeight(dp(48)); b.setMinimumWidth(0); b.setMinWidth(0);
        b.setPadding(dp(8),dp(4),dp(8),dp(4));
        GradientDrawable shape=new GradientDrawable(); shape.setColor(0xff192d38);
        shape.setCornerRadius(dp(22)); shape.setStroke(dp(1),0xff39545f);
        b.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x336ed3c4),shape,null));
        b.setOnClickListener(v->action.run()); return b;
    }
    private Button quietControl(String value,Runnable action) {
        Button button=control(value,action);button.setTextSize(10);button.setTextColor(0xff8d9bb1);
        button.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x227d99cc),null,new android.graphics.drawable.ColorDrawable(Color.WHITE)));
        return button;
    }
    private final class VoiceBars extends View {
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        VoiceBars(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);invalidate();}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;
            boolean moving=(scene.listening||scene.speaking)&&android.animation.ValueAnimator.areAnimatorsEnabled()&&hasWindowFocus();
            double time=scene.characterTime/250.0;ink.setColor(0xff9ee8dc);
            for(int i=0;i<12;i++) {float height=(moving?3+Math.abs((float)Math.sin(time+i*.65))*14:3)*density;
                float x=getWidth()/2f+(i-5.5f)*7*density;
                canvas.drawRoundRect(x,getHeight()/2f-height/2,x+2*density,getHeight()/2f+height/2,density,density,ink);
            }
            if(isShown()&&hasWindowFocus())postInvalidateDelayed(moving?33:250);
        }
    }
    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }

    private final class Scene extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Drawable avatar;
        PetSprite pet;
        String taskState="";
        final SimpleDateFormat clock=new SimpleDateFormat("HH:mm",Locale.ROOT);
        boolean connected,listening,speaking,running,reading,decision,foreground;
        String trace="", activity="等待下一次交互"; int characters;
        long feedSequence=-1, traceAge; boolean searching;
        long lastFrame, ambientTime, characterTime, idleAge;
        float closeness, focusAmount, zoom;
        final android.graphics.RectF characterBounds=new android.graphics.RectF();
        long reactionStart;
        boolean longReaction;
        int shownAccent=0xff9ee8dc, shownBase=0xff0c1420;
        java.util.function.Consumer<String> touchAction;
        final android.view.ScaleGestureDetector scaling;
        final android.view.GestureDetector gestures;
        boolean multiplePointers;
        Scene(Context c) {
            super(c);avatar=c.getDrawable(c.getResources().getIdentifier("placeholder_agent", "drawable", c.getPackageName()));
            setClickable(true);
            scaling=new android.view.ScaleGestureDetector(c,new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(android.view.ScaleGestureDetector detector){
                    zoom=Math.max(0,Math.min(1,zoom+(float)Math.log(detector.getScaleFactor())*1.4f));
                    revealNarration();invalidate();return true;
                }
                @Override public void onScaleEnd(android.view.ScaleGestureDetector detector){
                    getContext().getSharedPreferences("appearance",0).edit().putFloat("zoom",zoom).apply();
                }
            });
            scaling.setQuickScaleEnabled(false);scaling.setStylusScaleEnabled(false);
            gestures=new android.view.GestureDetector(c,new android.view.GestureDetector.SimpleOnGestureListener(){
                @Override public boolean onDown(android.view.MotionEvent event){return true;}
                @Override public boolean onSingleTapConfirmed(android.view.MotionEvent event){
                    if(multiplePointers)return false;
                    float d=getResources().getDisplayMetrics().density,x=event.getX()/d,y=event.getY()/d;
                    if(!characterBounds.contains(x,y))return false;
                    float position=(y-characterBounds.top)/characterBounds.height();
                    String action=position<.46f?"摸了一下头":position<.63f?"轻拍了一下肩膀":position<.8f?"戳了一下腰":"轻碰了一下脚";
                    react(false);revealNarration();if(touchAction!=null)touchAction.accept(action);return true;
                }
                @Override public boolean onDoubleTap(android.view.MotionEvent event){return true;}
            });
            gestures.setIsLongpressEnabled(false);
        }
        void react(boolean held){idleAge=0;longReaction=held;reactionStart=android.os.SystemClock.uptimeMillis();invalidate();}
        @Override public boolean performClick(){super.performClick();revealNarration();
            if(touchAction!=null)touchAction.accept("轻拍了一下肩膀");return true;}
        @Override public boolean onTouchEvent(android.view.MotionEvent event){
            int action=event.getActionMasked();
            if(action==android.view.MotionEvent.ACTION_DOWN)multiplePointers=false;
            if(event.getPointerCount()>1)multiplePointers=true;
            scaling.onTouchEvent(event);
            if(multiplePointers){
                android.view.MotionEvent cancel=android.view.MotionEvent.obtain(event);
                cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);gestures.onTouchEvent(cancel);cancel.recycle();
            }else gestures.onTouchEvent(event);
            return true;
        }
        void setFeed(HabitatFeed feed) {
            activity=feed.title;searching=feed.searching;
            if(feed.sequence!=feedSequence||!trace.equals(feed.trace)) {
                characters=feed.trace.startsWith(trace)?characters:0;
                feedSequence=feed.sequence;trace=feed.trace;traceAge=0;
                lastFrame=android.os.SystemClock.uptimeMillis();
            }
        }
        private float approach(float value,float target,float step){return value<target?Math.min(target,value+step):Math.max(target,value-step);}
        private int blend(int a,int b,float mix) {
            int r=Math.round(Color.red(a)+(Color.red(b)-Color.red(a))*mix);
            int g=Math.round(Color.green(a)+(Color.green(b)-Color.green(a))*mix);
            int blue=Math.round(Color.blue(a)+(Color.blue(b)-Color.blue(a))*mix);
            if(Math.abs(r-Color.red(b))<=2)r=Color.red(b);if(Math.abs(g-Color.green(b))<=2)g=Color.green(b);if(Math.abs(blue-Color.blue(b))<=2)blue=Color.blue(b);
            return Color.rgb(r,g,blue);
        }
        private void drawEnvironment(Canvas c,float w,float h,int accent,float calm) {
            float orbitY=h*(.59f+.08f*closeness),radius=Math.max(1,w*.7f);
            paint.setShader(new android.graphics.RadialGradient(w/2,h*.48f,radius,
                new int[]{0x3529325a,0x1529325a,0x0029325a},new float[]{0,.55f,1},android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,paint);paint.setShader(null);
            float horizon=h*.58f;
            paint.setShader(new android.graphics.LinearGradient(0,horizon,0,h,
                new int[]{0x007187a4,0x187187a4,0x007187a4},new float[]{0,.45f,1},android.graphics.Shader.TileMode.CLAMP));
            paint.setAlpha((int)(255*calm*(1-.7f*closeness)));paint.setStrokeWidth(.6f);
            for(int i=0;i<12;i++){float t=i/11f,y=horizon+(h-horizon)*t*t;c.drawLine(0,y,w,y,paint);}
            for(int i=-4;i<=12;i++)c.drawLine(w/2+(i-4)*8,horizon,(i-4)*w/4,h,paint);
            paint.setShader(null);paint.setAlpha(255);
            paint.setColor(accent);paint.setAlpha((int)(40*calm));c.drawLine(0,horizon,w,horizon,paint);
            android.graphics.Path beam=new android.graphics.Path();beam.moveTo(w*.45f,h*.2f);beam.lineTo(w*.55f,h*.2f);beam.lineTo(w*.72f,orbitY);beam.lineTo(w*.28f,orbitY);beam.close();
            paint.setShader(new android.graphics.LinearGradient(0,h*.2f,0,orbitY,0x009282dd,0x129282dd,android.graphics.Shader.TileMode.CLAMP));paint.setAlpha((int)(255*calm));c.drawPath(beam,paint);paint.setShader(null);paint.setAlpha(255);
            float rw=Math.min(w-12,240+70*closeness),rh=84+16*closeness;
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.7f);paint.setColor(accent);paint.setAlpha((int)(76*calm));
            c.drawOval((w-rw)/2,orbitY-rh/2,(w+rw)/2,orbitY+rh/2,paint);
            paint.setAlpha((int)(40*calm));paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{3,5},0));
            c.drawOval((w-rw)/2+12,orbitY-rh/2+12,(w+rw)/2-12,orbitY+rh/2-12,paint);paint.setPathEffect(null);
            if(listening) {float pulse=(characterTime%2000)/2000f;paint.setAlpha((int)(65*(1-pulse)));c.drawOval((w-rw)/2-12*pulse,orbitY-rh/2-6*pulse,(w+rw)/2+12*pulse,orbitY+rh/2+6*pulse,paint);}
            paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
            for(int i=0;i<12;i++) {
                double phase=ambientTime*Math.PI/3500+i*.73;float drift=(float)((1-Math.cos(phase))*.5);
                float x=w*(.08f+((i*37)%83)/100f)+8*drift,y=h*(.16f+((i*29)%65)/100f)-18*drift;
                paint.setColor(accent);paint.setAlpha((int)((35+45*(1-drift))*calm*(connected?1:.2f)));
                c.drawCircle(x,y,1,paint);paint.setAlpha((int)(8*calm));c.drawCircle(x,y,4,paint);
            }
            paint.setAlpha(255);
        }
        @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if(pet!=null) pet.pause(); invalidate(); }
        @Override protected void onVisibilityChanged(View changed,int visibility) { super.onVisibilityChanged(changed,visibility); if(pet!=null) pet.pause(); if(visibility==VISIBLE) invalidate(); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float d=getResources().getDisplayMetrics().density;
            c.save(); c.scale(d,d); float w=getWidth()/d,h=getHeight()/d;
            boolean compact=h<260;
            boolean focus=!hasWindowFocus()||listening||speaking||reading||decision||foreground;
            long now=android.os.SystemClock.uptimeMillis();
            long elapsed=lastFrame==0?0:Math.max(0,Math.min(100,now-lastFrame));lastFrame=now;
            boolean reduced=!android.animation.ValueAnimator.areAnimatorsEnabled();
            if(!focus&&!running&&connected&&!reduced)idleAge+=elapsed;
            else if(listening||speaking||running||decision)idleAge=0;
            boolean resting=idleAge>=90000&&!focus&&!running&&connected;
            if(hasWindowFocus()&&!reduced) {
                if(!focus&&connected)ambientTime+=elapsed;
                if(!reading&&!decision)characterTime+=elapsed;
            }
            closeness=approach(closeness,zoom,reduced?1:elapsed/700f);
            focusAmount=approach(focusAmount,focus?1:0,reduced?1:elapsed/800f);
            boolean failed=taskState.contains("failed")||taskState.contains("error");
            int targetAccent=!connected?0xff8b96a4:decision?0xffeac08a:failed?0xffe48c9c:searching?0xff8ccbf6:running?0xffb6a4ff:0xff9ee8dc;
            int targetBase=!connected?0xff141820:failed?0xff211521:decision?0xff211811:searching?0xff0a1825:0xff0c1420;
            shownAccent=blend(shownAccent,targetAccent,reduced?1:Math.min(1,elapsed/180f));
            shownBase=blend(shownBase,targetBase,reduced?1:Math.min(1,elapsed/180f));
            int accent=shownAccent;float calm=1-.88f*focusAmount;
            c.drawColor(shownBase);
            drawEnvironment(c,w,h,accent,calm);
            paint.setTypeface(Typeface.MONOSPACE); paint.setTextSize(compact?18:21); paint.setColor(blend(0xffa9b9ca,0xff3f4d60,focusAmount));
            c.drawText(clock.format(new Date()),4,compact?28:48,paint);
            paint.setTextSize(8); c.drawText(new SimpleDateFormat("EEE · MMM dd",Locale.getDefault()).format(new Date()),4,compact?46:66,paint);
            paint.setTextSize(10);paint.setColor(blend(accent,0xff687084,focusAmount));
            String headline=!connected?"本机连接中断 · 执行情况未知":decision?"等待你的决定":listening?"正在聆听":speaking?"正在回应":activity;
            String shortTitle=android.text.TextUtils.ellipsize(headline,new android.text.TextPaint(paint),Math.max(0,compact?w*.45f:w-12),android.text.TextUtils.TruncateAt.END).toString();
            c.drawText(shortTitle,4,compact?68:88,paint);
            if(!focus&&connected) {traceAge+=elapsed;characters=reduced?trace.length():Math.min(trace.length(),characters+(int)Math.max(1,elapsed/22));}
            float traceBottom=h-50;
            if(traceBottom>140&&traceAge<22000) {
                c.save();c.clipRect(4,105,w-4,traceBottom);
                paint.setTypeface(Typeface.MONOSPACE);paint.setTextSize(10);
                paint.setColor(focus?0xff354052:accent);
                int opacity=focus?80:(int)(140*Math.min(1,Math.max(0,(22000-traceAge)/4000f)));
                paint.setAlpha(opacity);
                // Wrap within the reserved background region; the character is drawn over it.
                java.util.ArrayList<String> rows=new java.util.ArrayList<>();
                for(String line:trace.substring(0,Math.min(characters,trace.length())).split("\n")) {
                    int at=0;
                    while(at<line.length()) {int count=paint.breakText(line,at,line.length(),true,Math.max(1,w-18),null);if(count==0)break;rows.add(line.substring(at,at+count));at+=count;}
                }
                int capacity=Math.max(1,Math.min(8,(int)((traceBottom-110)/17)));
                int start=Math.max(0,rows.size()-capacity);
                for(int i=start;i<rows.size();i++)c.drawText(rows.get(i),6,120+(i-start)*17,paint);
                paint.setAlpha(255);c.restore();
            }
            float reserve=30;
            float maxHeight=Math.max(0,h-reserve-115);
            float ah=compact?Math.max(0,Math.min(160,h-24)):Math.min((184+154*closeness)*(presentation==null?1:presentation.size),maxHeight+(reserve-30)*closeness),aw=ah*(pet==null?64f/82:192f/208);
            float top=compact?12:Math.max(112,Math.min((h-ah)*.40f,h-reserve-ah));
            float center=compact?w*.75f:w/2;
            // Adapted from OverlayService startPetIdle/petTapBounce/petLongPressPulse.
            // Copyright (c) 2026 zhou-gu24, MIT; see THIRD_PARTY_NOTICES.txt.
            float breath=reduced?0:(float)((1-Math.cos(characterTime*Math.PI/(resting?4800:2600)))*.5);
            top-=2*breath;
            characterBounds.set(center-aw/2,top,center+aw/2,top+ah);
            float anchor=(top+2*breath+ah)*d;
            if(Math.abs(characterBottom-anchor)>dp(2)){characterBottom=anchor;positionNarration();}
            float sx=1+.018f*breath,sy=1+.026f*breath;
            long reactionAge=now-reactionStart;
            if(!reduced&&reactionStart!=0&&reactionAge<390) {
                float progress=Math.min(1,reactionAge/(longReaction?390f:330f));
                float amount=1-new android.view.animation.OvershootInterpolator(1.8f).getInterpolation(progress);
                sx+=amount*(longReaction?.10f:.13f);
                sy+=amount*(longReaction?.10f:-.13f);
            }
            String visual=state.visible(connected,decision,listening,speaking,now);
            String animation=visual.equals("done")?"speaking":visual.equals("streaming")?"speaking":visual.equals("offline")?"idle":visual;
            boolean animate=pet!=null&&hasWindowFocus()&&!reading&&android.animation.ValueAnimator.areAnimatorsEnabled();
            boolean dim=reading||decision||resting;
            if(dim)c.saveLayerAlpha(0,0,w,h,resting?214:166);
            c.save();c.scale(sx,sy,center,top+ah);
            // Upstream sparse idle sway (850 ms out, 1050 ms back), adapted to a visible-time clock.
            float tilt=resting?-4:0;
            if(!reduced&&!focus&&!running&&!resting) {
                long gesture=idleAge%9000;
                if(gesture<1900&&idleAge>=9000){float t=gesture<850?gesture/850f:1-(gesture-850)/1050f;tilt=3*(float)((1-Math.cos(t*Math.PI))*.5);}
            }
            if(!reduced)c.rotate(tilt,center,top+ah);
            if(pet==null||!pet.draw(c,center-aw/2,top,aw,ah,animation,now,animate)) {
                float fallbackWidth=ah*64/82;
                avatar.setBounds((int)(center-fallbackWidth/2),(int)top,(int)(center+fallbackWidth/2),(int)(top+ah)); avatar.draw(c);
            }
            c.restore();
            if(dim)c.restore();
            if(running&&!focus&&closeness<.5f&&!compact) {
                float left=center-45,y=top+ah*.55f;
                paint.setColor(0xc9102e38);c.drawRect(left,y,left+90,y+44,paint);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.7f);paint.setColor(0x709ee8dc);c.drawRect(left,y,left+90,y+44,paint);paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x80a5e9d4);for(int i=0;i<3;i++)c.drawRect(left+8,y+10+i*8,left+8+(i==1?30:54),y+12+i*8,paint);
            }
            c.restore();
            boolean transition=Math.abs(closeness-zoom)>.001f||Math.abs(focusAmount-(focus?1:0))>.001f||shownAccent!=targetAccent||shownBase!=targetBase;
            if(isShown()&&getWindowVisibility()==VISIBLE&&hasWindowFocus())
                postInvalidateDelayed(!reduced&&(!reading&&!decision||transition)||animate||traceAge<22000&&!trace.isEmpty()&&!focus?33:1000);
        }
    }
}
