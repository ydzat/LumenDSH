package local.sliverwolf.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static local.sliverwolf.assistant.DshTransport.object;

/** Native conversation UI; backend decisions remain explicit human actions. */
public final class MainActivity extends Activity {
    private SessionController controller;
    private DeviceBridge deviceBridge;
    private TextView connection, session, status, history;
    private EditText draft;
    private ScrollView transcript;
    private android.widget.ImageButton send;
    private Button decisions, older, delivery, cancel;
    private final Handler main = new Handler();
    private boolean renderQueued;
    private AlertDialog decisionDialog;
    private String dialogId = "";
    private VoiceInput voiceInput;
    private TextView voiceStatus;
    private CheckBox voiceSend;
    private String voiceSession = "", voiceRequest = "";
    private boolean voiceAutoSend;
    private HabitatView habitat;
    private String renderedPresentation="", renderedProfile="";
    private boolean releaseHealthRunning;
    private PetStore petStore;
    private PetStore.Pack activePet;
    private PetSprite defaultPet;
    private boolean petBusy;
    private AssistantProfile.Store profileStore;
    private AssistantProfile activeProfile;
    private static final java.util.concurrent.ExecutorService petIo=java.util.concurrent.Executors.newSingleThreadExecutor();
    private android.widget.FrameLayout pages;
    private LinearLayout transcriptPage;
    private HabitatFeed habitatFeed=new HabitatFeed("等待下一次交互","",-1,false);
    private String habitatSession = "", habitatTrace = "", habitatSnapshot = "";
    private final Runnable habitatTick = new Runnable() {
        @Override public void run() { if (!isDestroyed()) { renderHabitat(); main.postDelayed(this, 250); } }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);if(saved!=null){documentPending=saved.getBoolean("documentPending",false);presentationExport=saved.getString("presentationExport");}
        try{new ContentReleases(this).recover();}catch(Exception error){throw new IllegalStateException("Content recovery failed",error);}
        LinearLayout root = column();
        transcriptPage = root; root.setBackgroundColor(0xff0c1420);
        android.widget.ImageButton back=HabitatView.iconControl(this,"ic_chevron_down","返回角色空间",()->showTranscript(false));
        int touchSize=Math.round(48*getResources().getDisplayMetrics().density);
        root.addView(back,new LinearLayout.LayoutParams(touchSize,touchSize));
        int padding = (int) (12 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding);
        connection = label(root, "Connecting…", 13);
        session = label(root, "Choose a conversation", 16);
        session.setSingleLine(true);
        session.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout navigation = row(root); navigation.setVisibility(android.view.View.GONE);
        button(navigation, "Sessions", () -> chooseSession());
        button(navigation, "New", () -> new AlertDialog.Builder(this).setTitle("New conversation?")
            .setMessage("This explicitly creates a DSH conversation. Existing conversations remain available.")
            .setNegativeButton("返回", null).setPositiveButton("Create", (d, w) -> controller.create()).show());
        LinearLayout connectivity = row(root); connectivity.setVisibility(android.view.View.GONE);
        button(connectivity, "Reconnect", () -> controller.connect());
        button(connectivity, "Disconnect", () -> controller.disconnect());
        button(connectivity, "Device tools", this::showDeviceTools);
        status = label(root, "", 14);
        LinearLayout actions = row(root);
        older = button(actions, "更早记录", () -> controller.loadHistory(true));
        decisions = button(actions, "待处理", () -> showDecision());
        cancel = button(actions, "停止任务", () -> new AlertDialog.Builder(this).setTitle("停止当前任务？")
            .setMessage("将请求 DSH 取消当前执行轮次，已经排队的消息仍可能继续执行。")
            .setNegativeButton("返回", null).setPositiveButton("停止本轮", (d, w) -> controller.cancel()).show());
        delivery = button(root, "Review uncertain delivery", () -> new AlertDialog.Builder(this)
            .setTitle("Delivery record")
            .setMessage("Request: " + controller.pendingId + "\nState: " + controller.pendingState
                + "\n\nReconnect and inspect history before sending again. Clearing this record does not prove the original request was rejected, and never resends it.")
            .setNegativeButton("Keep record", null)
            .setPositiveButton("Acknowledge uncertainty", (d, w) -> controller.acknowledgeUnknown()).show());
        ScrollView scroll = new ScrollView(this);
        transcript = scroll;
        history = new TextView(this); history.setTextSize(16); history.setTextIsSelectable(true);
        history.setTextColor(0xffdce3ef);history.setLineSpacing(8,1.1f);
        history.setPadding(padding,padding,padding,padding);
        scroll.addView(history);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        draft = new EditText(this); draft.setHint("输入消息，或返回空间使用语音");
        draft.setMinLines(1); draft.setMaxLines(4);
        draft.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        if (saved != null) draft.setText(saved.getString("draft", ""));
        draft.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        LinearLayout composer=row(root);
        composer.addView(draft,new LinearLayout.LayoutParams(0,-2,1));
        send = HabitatView.iconControl(this,"ic_arrow_up","发送",()->{if(controller.send(draft.getText().toString()))draft.setText("");});
        composer.addView(send);
        send.setLayoutParams(new LinearLayout.LayoutParams((int)(76*getResources().getDisplayMetrics().density),-2));
        if(getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            connection.setVisibility(android.view.View.GONE);session.setVisibility(android.view.View.GONE);status.setMaxLines(1);
        }
        voiceStatus = label(root, "我在。", 13); voiceStatus.setVisibility(android.view.View.GONE);
        voiceSend = new CheckBox(this); voiceSend.setText("Auto-send voice and read reply"); root.addView(voiceSend); voiceSend.setVisibility(android.view.View.GONE);
        LinearLayout voiceActions = row(root); voiceActions.setVisibility(android.view.View.GONE);
        button(voiceActions, "Speak (Chinese)", this::startVoice);
        button(voiceActions, "Stop voice", () -> stopVoice("Voice stopped"));
        pages = new android.widget.FrameLayout(this);
        pages.addView(root, new android.widget.FrameLayout.LayoutParams(-1,-1));
        habitat = new HabitatView(this, new HabitatView.Actions() {
            public void voice() {
                if (voiceInput != null && (voiceInput.active() || deviceBridge.isSpeaking())) stopVoice("语音已停止");
                else startVoice();
            }
            public void stop() { stopVoice("语音已停止"); }
            public void transcript() { showTranscript(true); }
            public void decisions() { showDecision(); }
            public void settings() { showHabitatMenu(); }
            public void touch(String action) { submitCharacterTouch(action); }
        });
        pages.addView(habitat,new android.widget.FrameLayout.LayoutParams(-1,-1));
        root.setVisibility(android.view.View.GONE);
        setContentView(pages);
        getWindow().setStatusBarColor(0xff0c1420); getWindow().setNavigationBarColor(0xff0c1420);
        controller = new SessionController(getSharedPreferences("session", MODE_PRIVATE), this::scheduleRender);
        controller.connect();
        petIo.execute(()->{try{ScheduleAlarms.restore(this);}catch(Exception error){main.post(()->{if(!isDestroyed())petError(error);});}});
        openNotification(getIntent());
        if(saved==null)main.post(()->receiveShare(getIntent()));
        deviceBridge = SharedDeviceBridge.acquire(this);
        voiceInput = new VoiceInput(this, new VoiceInput.Listener() {
            public void state(String state) { voiceStatus.setText(state); renderHabitat(); }
            public void result(String text) {
                if (!voiceSession.equals(controller.selected)) { voiceStatus.setText("Conversation changed; speech discarded"); return; }
                if (!draft.getText().toString().trim().isEmpty()) {
                    voiceStatus.setText("Draft changed while listening; speech not submitted"); return;
                }
                draft.setText(text);
                if (voiceAutoSend && controller.send(text)) {
                    voiceRequest = controller.pendingId; draft.setText("");
                    voiceStatus.setText("Voice submitted; waiting for this reply");
                } else voiceStatus.setText("Recognized draft; review and tap Send");
            }
        });
        deviceBridge.microphoneActive = voiceInput::active;
        voiceSend.setChecked(getSharedPreferences("voice",MODE_PRIVATE).getBoolean("autoSend",false));
        voiceSend.setOnCheckedChangeListener((button, checked) -> getSharedPreferences("voice",MODE_PRIVATE).edit().putBoolean("autoSend",checked).apply());
        try {
            petStore=new PetStore(new java.io.File(getFilesDir(),"appearances"));
            profileStore=new AssistantProfile.Store(new java.io.File(getFilesDir(),"assistant-profiles"));
            deviceBridge.holdProfile(true);
            loadPet(null,false);
        } catch(Exception e) { petError(e); }
        if(saved!=null&&saved.getBoolean("transcriptVisible",false))showTranscript(true);
    }
    private void startVoice() {
        if(WakeVoiceService.microphoneBusy()||OwnerEnrollment.active){voiceStatus.setText("正在释放后台麦克风，请稍候");return;}
        if(petBusy) {voiceStatus.setText("配置正在校验，请稍候");return;}
        if (!controller.canSend() || controller.running || !voiceRequest.isEmpty()) {
            voiceStatus.setText("Wait for the current request and connection before speaking"); return;
        }
        if (!draft.getText().toString().trim().isEmpty()) { voiceStatus.setText("Send or clear the existing draft first"); return; }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setTitle("Enable speech input?")
                .setMessage("The installed recognition service may send audio to its provider. This client does not retain recordings. Tap Speak again after granting microphone access.")
                .setNegativeButton("返回", null).setPositiveButton("Continue", (d, w) -> requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 31)).show();
            return;
        }
        deviceBridge.stopSpeaking(); voiceSession = controller.selected; voiceAutoSend = voiceSend.isChecked();
        voiceInput.start(activeProfile==null?"zh-CN":activeProfile.voice.language);
    }
    private void stopVoice(String reason) {
        if (voiceInput != null) voiceInput.stop();
        voiceRequest = "";
        if (deviceBridge != null) deviceBridge.stopSpeaking();
        if (voiceStatus != null) voiceStatus.setText(reason);
    }
    private void updateVoice() {
        if (voiceInput == null) return;
        if ((!voiceSession.equals(controller.selected) || !controller.connected)
                && (voiceInput.active() || !voiceRequest.isEmpty())) {
            stopVoice("Voice stopped: connection or conversation changed"); return;
        }
        if (voiceRequest.isEmpty()) return;
        VoiceReply reply=VoiceReply.find(controller.conversation,voiceRequest);
        if(reply==null)return;
        String key="voice-"+voiceRequest;voiceRequest="";
        if(reply.text.isEmpty()){voiceStatus.setText(reply.notice);return;}
        try{deviceBridge.speakReply(key,reply.text);voiceStatus.setText("Reply sent to speech engine; tap Speak for the next request");}
        catch(Exception error){voiceStatus.setText("Reply playback unavailable: "+error.getMessage());}
    }
    private void showDeviceTools() {
        new AlertDialog.Builder(this).setTitle("Device tools")
            .setMessage(deviceBridge.state + "\n\nAvailable: status, notification, explicit speech, operation lookup, and artifact handoff. Pair only with your local Termux adapter.")
            .setNegativeButton("Close", null)
            .setNeutralButton("Stop speaking", (d, w) -> deviceBridge.stopSpeaking())
            .setPositiveButton("Pair Termux", (d, w) -> {
                TextView credential = new TextView(this);
                credential.setText(deviceBridge.pairingToken()); credential.setTextIsSelectable(true);
                new AlertDialog.Builder(this).setTitle("Private pairing token")
                    .setView(credential).setPositiveButton("Done", null).show();
            }).show();
    }
    @Override protected void onResume() { super.onResume(); if(voiceStatus!=null&&"已暂停前台语音".contentEquals(voiceStatus.getText()))voiceStatus.setText("我在。"); WakeVoiceService.activity(true); if(getSharedPreferences("wake",0).getBoolean("enabled",false)&&new OwnerVoiceStore(this).exists()&&checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED&&!WakeVoiceService.running())startForegroundService(new android.content.Intent(this,WakeVoiceService.class)); CompanionOverlayService.activity(true); if(getSharedPreferences("overlay",0).getBoolean("enabled",false)&&android.provider.Settings.canDrawOverlays(this))startForegroundService(new android.content.Intent(this,CompanionOverlayService.class)); CompletionService.foreground=true; if(getSharedPreferences("completion",0).getBoolean("enabled",false))startForegroundService(new android.content.Intent(this,CompletionService.class)); if (deviceBridge != null) deviceBridge.visible = true; main.removeCallbacks(habitatTick); main.post(habitatTick); }
    @Override protected void onPause() { CompanionOverlayService.activity(documentPending); CompletionService.foreground=false; main.removeCallbacks(habitatTick); stopVoice("已暂停前台语音"); WakeVoiceService.activity(documentPending); if (deviceBridge != null) deviceBridge.visible = false; super.onPause(); }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout;
    }
    private LinearLayout row(LinearLayout parent) {
        LinearLayout layout = new LinearLayout(this); parent.addView(layout); return layout;
    }
    private TextView label(LinearLayout parent, String text, int size) {
        TextView label = new TextView(this); label.setText(text); label.setTextSize(size); label.setTextColor(0xffc6d0e2);label.setLineSpacing(4,1.05f); parent.addView(label); return label;
    }
    private Button button(LinearLayout parent, String text, Runnable action) {
        Button button = new Button(this); button.setText(text); button.setAllCaps(false); button.setTextSize(12);
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
        shape.setColor(0xff1b293b);shape.setCornerRadius(18*getResources().getDisplayMetrics().density);
        shape.setStroke(1,0xff39465e);
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x337d99cc),shape,null));
        button.setMinHeight((int)(48*getResources().getDisplayMetrics().density));
        button.setMinimumHeight((int)(48*getResources().getDisplayMetrics().density));
        button.setTextColor(0xffcee3eb);
        button.setOnClickListener(view -> action.run());
        if (parent.getOrientation() == LinearLayout.HORIZONTAL) parent.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        else parent.addView(button);
        return button;
    }
    private ScrollView readableDetails(String text) {
        ScrollView scroll=new ScrollView(this);TextView body=new TextView(this);
        int pad=(int)(20*getResources().getDisplayMetrics().density);
        body.setPadding(pad,pad,pad,pad);body.setText(text);body.setTextSize(15);
        body.setTextColor(0xffdce3ef);body.setLineSpacing(6,1.1f);body.setTextIsSelectable(true);
        scroll.addView(body);return scroll;
    }
    private CharSequence styledTranscript(String text) {
        android.text.SpannableString styled=new android.text.SpannableString(text);
        java.util.regex.Matcher roles=java.util.regex.Pattern.compile("(?m)^(You|Assistant(?: \\(streaming\\))?|Tool result|Tool:.*)$").matcher(text);
        while(roles.find()) {
            int color=roles.group().startsWith("You")?0xffa7dcd5:roles.group().startsWith("Tool")?0xff8194b0:0xffc2b0ef;
            styled.setSpan(new android.text.style.ForegroundColorSpan(color),roles.start(),roles.end(),0);
            styled.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),roles.start(),roles.end(),0);
        }
        return styled;
    }
    private void scheduleRender() {
        if (renderQueued) return;
        renderQueued = true;
        main.postDelayed(() -> { renderQueued = false; if (!isDestroyed()) render(); }, 80);
    }
    private void render() {
        if(modelDetails!=null)modelDetails.setText(ModelSettings.describe(controller.modelInfo,controller.modelError));
        updateVoice();
        connection.setText(controller.connection);
        session.setText(controller.selected.isEmpty() ? "尚未选择会话" : "当前会话 · " + controller.selected.substring(Math.max(0,controller.selected.length()-8)));
        session.setContentDescription("当前会话标识："+controller.selected);
        session.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("会话信息").setView(readableDetails(controller.selected)).setPositiveButton("返回",null).show());
        status.setText(controller.taskState() + (controller.notice.isEmpty() ? "" : "\n" + controller.notice)
            + (controller.pendingId.isEmpty() ? "" : "\nDelivery: " + controller.pendingState));
        String text = controller.conversation.text();
        if (!text.contentEquals(history.getText())) {
            boolean nearBottom = history.getHeight() - transcript.getHeight() - transcript.getScrollY() < 100;
            history.setText(styledTranscript(text));
            if (nearBottom) transcript.post(() -> transcript.scrollTo(0, history.getBottom()));
        }
        updateHabitatContent();
        renderHabitat();
        send.setEnabled(controller.canSend());
        cancel.setEnabled(controller.connected && !controller.selected.isEmpty());
        older.setEnabled(controller.connected && controller.conversation.hasMore && !controller.historyBusy);
        decisions.setText("待处理 · " + controller.decisions.size());
        decisions.setEnabled(controller.connected && !controller.decisions.isEmpty());
        delivery.setVisibility(controller.pendingId.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        if (decisionDialog != null && !controller.decisions.containsKey(dialogId)) { decisionDialog.dismiss(); decisionDialog = null; }
    }
    private void showTranscript(boolean show) {
        if (!show) ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(draft.getWindowToken(),0);
        transcriptPage.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
        habitat.setVisibility(show ? android.view.View.GONE : android.view.View.VISIBLE);
        if (show) {
            stopVoice("阅读文字记录中");
            transcript.post(()->transcript.fullScroll(android.view.View.FOCUS_DOWN));
        }
        else if ("阅读文字记录中".contentEquals(voiceStatus.getText())) voiceStatus.setText("我在。");
    }
    @Override public void onBackPressed() {
        if (transcriptPage.getVisibility()==android.view.View.VISIBLE) showTranscript(false);
        else super.onBackPressed();
    }
    private void showLocalUpdate(){
        petIo.execute(()->{try{
            NativeReleases releases=new NativeReleases(this);JSONObject status=releases.status();
            main.post(()->new AlertDialog.Builder(this).setTitle("本地更新")
                .setMessage("当前版本："+status.optLong("installedCode")+"\n候选版本："+status.optLong("targetCode")+"\n状态："+status.optString("state")+"\n"+status.optString("detail")+"\n更新由 Android 确认安装，会短暂中断客户端语音。")
                .setNegativeButton("返回",null)
                .setNeutralButton("安装权限",(d,w)->startActivity(new android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,android.net.Uri.parse("package:"+getPackageName()))))
                .setPositiveButton("安装候选版本",(d,w)->petIo.execute(()->{try{releases.install(deviceBridge.visible&&!deviceBridge.isSpeaking()&&!voiceInput.active()&&!WakeVoiceService.microphoneBusy()&&!deviceBridge.releases.unsettled());}catch(Exception error){main.post(()->petError(error));}})).show());
        }catch(Exception error){main.post(()->petError(error));}});
    }
    private void showMemory(int page) {
        final String session = controller.selected;
        if (session.isEmpty()) { new AlertDialog.Builder(this).setMessage("请先选择会话").setPositiveButton("返回",null).show(); return; }
        petIo.execute(()->{
            try {
                DshTransport transport = new DshTransport(getSharedPreferences("session",MODE_PRIVATE));
                JSONObject value = transport.memoryRead(page==0 ? "status-summary" : page==1 ? "runtime-memory" : "list", session);
                StringBuilder text = new StringBuilder();
                if (page==2) {
                    JSONArray records=value.getJSONArray("items");
                    for(int i=0;i<records.length();i++) {
                        JSONObject record=records.getJSONObject(i);
                        text.append("来源：").append(record.optString("id",record.optString("documentId","未知"))).append("\n");
                        text.append(record.optString("memoryBodyName", "长期记忆")).append(" · ").append(record.optString("createdAt", record.optString("created_at"))).append("\n")
                            .append(record.getString("content")).append("\n\n");
                    }
                    if(records.length()==0) text.append("当前工作区暂无长期记忆。 ");
                    text.append("\n最多显示 100 条；可在对话中检索、更正或删除指定记忆。");
                } else if (page==1) {
                    JSONArray records = value.getJSONArray("entries");
                    for (int i=0;i<records.length();i++) {
                        JSONObject record = records.getJSONObject(i);
                        text.append("user".equals(record.getString("target")) ? "用户偏好" : "工作记忆")
                            .append(" · ").append(record.getString("updated_at")).append("\n")
                            .append(record.getString("content")).append("\n\n");
                    }
                    if (records.length()==0) text.append("尚无工作记忆或用户偏好记录。");
                    text.append("\n此处仅显示常驻上下文的记忆；长期记忆空间由 Agent 按需召回。");
                } else {
                    JSONObject semantic = transport.memoryRead("embedding-status", session);
                    text.append("语义检索：").append(semantic.getBoolean("available") ? "本地向量服务可用" : "不可用，请恢复向量服务")
                        .append("\n记忆向量：").append(semantic.getInt("embedded")).append(" / ").append(semantic.getInt("totalInsights"))
                        .append("\n").append(semantic.getString("model")).append("\n\n");
                    text.append("Mnemon ").append(value.optString("dshMnemonVersion", "未知版本"))
                        .append("\n记忆引擎：").append(value.optBoolean("healthy") ? "可用" : "尚未就绪")
                        .append("\n记忆写入：").append(value.optBoolean("writeEnabled") ? "已启用" : "已暂停")
                        .append("\n\n沿用当前会话与身份。可以在对话中要求记住、查找或更正信息。");
                }
                String body=text.toString();
                main.post(()->{
                    if(isDestroyed() || !session.equals(controller.selected)) return;
                    TextView content=new TextView(this); content.setText(body); content.setTextIsSelectable(true);
                    int pad=(int)(20*getResources().getDisplayMetrics().density);
                    content.setPadding(pad,pad,pad,pad);
                    ScrollView scroll=new ScrollView(this); scroll.addView(content);
                    AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(page==1?"工作记忆与偏好":"长期记忆")
                        .setView(scroll).setNegativeButton("返回",null).setNeutralButton("刷新",(d,w)->showMemory(page));
                    if(page==0) dialog.setPositiveButton("查看记录",(d,w)->new AlertDialog.Builder(this).setTitle("记忆记录").setItems(new String[]{"工作记忆与偏好","长期记忆空间","整理文档与日记"},(pick,index)->{if(index==2)showMemoryDocuments(null);else showMemory(index+1);}).show());
                    dialog.show();
                });
            } catch(Exception error) {
                main.post(()->{
                    if(isDestroyed() || !session.equals(controller.selected)) return;
                    new AlertDialog.Builder(this).setTitle("记忆服务不可用")
                        .setMessage("尚未启用兼容的 Mnemon 服务，或连接暂时不可用。不会以本地占位记录替代。\n"+error.getClass().getSimpleName())
                        .setPositiveButton("返回",null).show();
                });
            }
        });
    }
    private void showMemoryDocuments(String id) {
        final String session=controller.selected;
        petIo.execute(()->{try{
            JSONObject data=new DshTransport(getSharedPreferences("session",MODE_PRIVATE)).memoryRead(id==null?"documents":"document",session,id);
            main.post(()->{if(isDestroyed()||!session.equals(controller.selected))return;try{
                if(id==null) {
                    JSONArray docs=data.getJSONArray("documents");String[] titles=new String[docs.length()];
                    for(int i=0;i<titles.length;i++){JSONObject doc=docs.getJSONObject(i);titles[i]=doc.getString("title")+"\n"+doc.getString("updatedAt")+" · "+doc.getString("status");}
                    AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("整理文档与日记").setNegativeButton("返回",null);
                    if(titles.length==0)dialog.setMessage("尚无整理文档。不会用预设内容替代。");
                    else dialog.setItems(titles,(d,index)->{JSONObject doc=docs.optJSONObject(index);showMemoryDocuments(doc.optString("id"));});
                    dialog.show();
                } else {
                    TextView text=new TextView(this);text.setTextIsSelectable(true);text.setPadding(28,16,28,16);
                    text.setText("来源："+data.getString("id")+"\n更新："+data.getString("updatedAt")+"\n版本："+data.getInt("revision")+
                        "\n关联会话："+data.getJSONArray("sessionIds").toString()+"\n引用来源："+data.getJSONArray("sourcePaths").toString()+"\n\n"+data.getString("content"));
                    ScrollView scroll=new ScrollView(this);scroll.addView(text);
                    new AlertDialog.Builder(this).setTitle(data.getString("title")).setView(scroll).setPositiveButton("返回",null).show();
                }
            }catch(Exception e){petError(e);}});
        }catch(Exception e){main.post(()->petError(e));}});
    }
    private TextView modelDetails;
    private void showModelSettings() {
        modelDetails=new TextView(this);modelDetails.setPadding(32,24,32,24);modelDetails.setTextIsSelectable(true);
        modelDetails.setText(ModelSettings.describe(controller.modelInfo,controller.modelError));
        ScrollView scroll=new ScrollView(this);scroll.addView(modelDetails);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("模型与上下文").setView(scroll)
            .setPositiveButton("返回",null).create();
        dialog.setOnDismissListener(d->modelDetails=null);dialog.show();controller.refreshModel();
    }
    private void showHabitatMenu() {
        String[] choices = {"选择会话", "语音自动发送与朗读：" + (voiceSend.isChecked()?"开":"关"),
            "重新连接", "断开客户端连接", "设备工具与配对", "文字记录与任务控制", "新建会话", "角色形象", "组合配置", "资源管理", "空间展示设置", "完成提醒与历史", "分享收件箱", "定时任务", "悬浮角色", "自动语音待命", "当前任务状态", "本地更新", "长期记忆", "自主活动与行为", "模型与上下文"};
        new AlertDialog.Builder(this).setTitle("LumenDSH · 设置").setItems(choices,(d,w)-> {
            if(w==20) showModelSettings();
            else if(w==19) new CompanionSettings(this,petIo,controller.selected).show();
            else if(w==18) showMemory(0);
            else if(w==17) showLocalUpdate();
            else if(w==0) chooseSession();
            else if(w==1) voiceSend.setChecked(!voiceSend.isChecked());
            else if(w==2) controller.connect();
            else if(w==3) controller.disconnect();
            else if(w==4) showDeviceTools();
            else if(w==5) showTranscript(true);
            else if(w==7) showAppearance();
            else if(w==8) showProfiles();
            else if(w==9) showLibrary();
            else if(w==10) showPresentationSettings();
            else if(w==11) showCompletionSettings();
            else if(w==12) showShareInbox();
            else if(w==13) showSchedules();
            else if(w==14) showOverlaySettings();
            else if(w==15) showWakeSettings();
            else if(w==16) {
                updateHabitatContent();HabitatFeed feed=HabitatFeed.from(controller.conversation);
                showTranscript(false);habitat.offerSource(feed.source,"event:"+feed.sequence,
                    (controller.connected?"":"连接中断 · 以下为最近已知状态\n")+feed.title+(feed.trace.isEmpty()?"":"\n"+feed.trace));
            }
            else new AlertDialog.Builder(this).setTitle("新建会话？")
                .setMessage("这会创建新的 DSH 会话；现有会话仍然保留。")
                .setNegativeButton("返回",null).setPositiveButton("创建",(dialog,which)->controller.create()).show();
        }).setNegativeButton("返回",null).show();
    }
    @Override protected void onNewIntent(android.content.Intent intent){super.onNewIntent(intent);setIntent(intent);openNotification(intent);receiveShare(intent);}
    private void openNotification(android.content.Intent intent) {
        String selected=intent.getStringExtra("notificationSession");intent.removeExtra("notificationSession");
        if(selected!=null&&!selected.isEmpty()&&controller!=null)controller.select(selected);
    }
    private void showWakeSettings() {
        try {
            boolean enabled=WakeVoiceService.running();
            String phrase="嗨，"+PresentationSettings.load(this).name.trim();
            new AlertDialog.Builder(this).setTitle("自动语音待命")
                .setMessage("唤醒词："+phrase+"\n须先录入主人声纹。离开主界面后，在本机检测完整唤醒词并验证主人声纹。未通过验证时保持静默。听到提示音后再说指令，自动发送到当前会话并朗读回复。\n系统语音识别可能联网；本机唤醒不代表离线识别。通知栏可随时停止。当前中文模型仅支持中文角色名。\n\n"+WakeVoiceService.status())
                .setPositiveButton(enabled?"关闭待命":"开启待命",(d,w)->{
                    if(!enabled&&!new OwnerVoiceStore(this).exists()){startActivity(new android.content.Intent(this,OwnerVoiceActivity.class));return;}
                    if(!enabled&&checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
                        requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},91);return;
                    }
                    getSharedPreferences("wake",0).edit().putBoolean("enabled",!enabled).apply();
                    android.content.Intent intent=new android.content.Intent(this,WakeVoiceService.class);
                    if(enabled)stopService(intent);else startForegroundService(intent);
                }).setNeutralButton("主人声纹",(d,w)->startActivity(new android.content.Intent(this,OwnerVoiceActivity.class))).setNegativeButton("返回",null).show();
        }catch(Exception error){voiceStatus.setText(error.getMessage());}
    }
    private void showOverlaySettings() {
        android.content.SharedPreferences settings=getSharedPreferences("overlay",0);
        boolean enabled=settings.getBoolean("enabled",false),allowed=android.provider.Settings.canDrawOverlays(this);
        new AlertDialog.Builder(this).setTitle("悬浮角色")
            .setMessage("离开主界面后显示角色和真实任务状态，可拖动贴边。悬浮窗没有语音按钮；隐藏和召回放在通知中。自动唤醒可在菜单的“自动语音待命”中开启；手动语音保留在主界面。\n"+settings.getString("error",""))
            .setPositiveButton(!allowed?"授权悬浮窗":enabled?"关闭悬浮角色":"开启悬浮角色",(d,w)->{
                if(!allowed){startActivity(new android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+getPackageName())));return;}
                settings.edit().putBoolean("enabled",!enabled).putBoolean("hidden",false).remove("error").apply();
                android.content.Intent intent=new android.content.Intent(this,CompanionOverlayService.class);
                if(enabled)stopService(intent);else startForegroundService(intent);
            }).setNeutralButton("召回",(d,w)->{
                if(enabled&&allowed)startForegroundService(new android.content.Intent(this,CompanionOverlayService.class).setAction("recall"));
            }).setNegativeButton("返回",null).show();
    }
    private void showSchedules() {
        try {
            ScheduledTaskStore store=new ScheduledTaskStore(getFilesDir());
            java.util.List<ScheduledTaskStore.Task> tasks=store.list();
            String[] labels=new String[tasks.size()];
            for(int i=0;i<labels.length;i++) {
                ScheduledTaskStore.Task task=tasks.get(i);
                labels[i]=(store.isPaused(task.id)?"已暂停 · ":"已启用 · ")+
                    android.text.format.DateFormat.format("MM-dd HH:mm",task.triggerAt)+" · "+task.text.substring(0,Math.min(32,task.text.length()));
            }
            new AlertDialog.Builder(this).setTitle("定时任务 · "+tasks.size())
                .setItems(labels,(d,w)->showSchedule(tasks.get(w)))
                .setPositiveButton("新建",(d,w)->createSchedule())
                .setNeutralButton("记忆整理",(d,w)->createSchedule("在当前会话中做一次简短的本地记忆整理：只依据可访问的对话和已有记忆，检查是否有一条值得保留的偏好、纠正或共同经历；先查重，最多新增或修正一条，不虚构经历、不保存秘密，不向外发送消息、不启动其他周期任务。若无合适内容就跳过。最后简短说明实际结果。"))
                .setNegativeButton("返回",null).show();
        }catch(Exception error){petError(error);}
    }
    private void createSchedule() { createSchedule(""); }
    private void createSchedule(String initialText) {
        if(controller.selected.isEmpty()){petError(new IllegalStateException("Select a conversation first"));return;}
        String selected=controller.selected;
        LinearLayout form=column();
        label(form,"到时提交到当前会话。应用关闭也可触发；强行停止后需重新打开。",14);
        if(!initialText.isEmpty()) label(form,"记忆整理每天最多触发一次；会话忙碌时跳过，不打断任务。",13);
        EditText text=new EditText(this);text.setHint("要交给 Agent 的任务");text.setText(initialText);form.addView(text);
        label(form,"从现在起等待多少分钟（1–10080）",13);
        EditText delay=new EditText(this);delay.setInputType(2);delay.setText("1");form.addView(delay);
        label(form,"重复间隔分钟（0表示仅一次）",13);
        EditText repeat=new EditText(this);repeat.setInputType(2);repeat.setText("0");form.addView(repeat);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("新建定时任务").setView(form)
            .setPositiveButton("保存并启用",null).setNegativeButton("返回",null).create();
        dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                int minutes=Integer.parseInt(delay.getText().toString()),interval=Integer.parseInt(repeat.getText().toString());
                String prompt=text.getText().toString().trim();
                if(minutes<1||minutes>10080||interval<0||interval>10080||prompt.isEmpty()||prompt.length()>8192)
                    throw new IllegalArgumentException("Task text or schedule interval is invalid");
                if(!initialText.isEmpty() && interval>0 && interval<1440)
                    throw new IllegalArgumentException("Memory reflection interval must be at least 1440 minutes");
                ScheduledTaskStore.Task task=new ScheduledTaskStore.Task((initialText.isEmpty()?"":"companion-")+java.util.UUID.randomUUID().toString(),
                    System.currentTimeMillis()+minutes*60000L,interval==0?"once":"interval",interval,prompt).withSession(selected);
                ScheduledTaskStore store=new ScheduledTaskStore(getFilesDir());store.put(task);
                try{ScheduleAlarms.register(this,task);}catch(Exception failure){store.pause(task.id,true);throw failure;}
                dialog.dismiss();showSchedules();
            }catch(Exception error){petError(error);}
        });
    }
    private void showSchedule(ScheduledTaskStore.Task task) {
        try {
            ScheduledTaskStore store=new ScheduledTaskStore(getFilesDir());boolean paused=store.isPaused(task.id);
            String fallback=store.deliveredAt(task.id)>0?"delivery-unknown · occurrence consumed":"not submitted";
            android.content.SharedPreferences outcomes=getSharedPreferences("schedules",0);
            String result=outcomes.getLong(task.id+":at",0)==store.deliveredAt(task.id)?outcomes.getString(task.id,fallback):fallback;
            new AlertDialog.Builder(this).setTitle("定时任务")
                .setMessage(task.text+"\n\n会话："+task.sessionId.substring(0,Math.min(8,task.sessionId.length()))+
                    "\n"+result+"\n暂停或取消不撤回已进入提交流程的轮次。错过的任务不会在重新打开时补发。")
                .setPositiveButton(paused?"一分钟后启用":"暂停",(d,w)->{
                    try {
                        if(paused){ScheduledTaskStore.Task next=task.at(System.currentTimeMillis()+60000);store.put(next);store.pause(task.id,false);
                            try{ScheduleAlarms.register(this,next);}catch(Exception failure){store.pause(task.id,true);throw failure;}}
                        else {store.pause(task.id,true);ScheduleAlarms.cancel(this,task);}
                        showSchedules();
                    }catch(Exception error){petError(error);}
                }).setNeutralButton("取消任务",(d,w)->{
                    try{store.cancel(task.id);ScheduleAlarms.cancel(this,task);showSchedules();}catch(Exception error){petError(error);}
                }).setNegativeButton("返回",null).show();
        }catch(Exception error){petError(error);}
    }
    private void receiveShare(android.content.Intent intent) {
        String action=intent.getAction();
        if(!android.content.Intent.ACTION_SEND.equals(action)&&!android.content.Intent.ACTION_SEND_MULTIPLE.equals(action))return;
        android.content.Intent incoming=new android.content.Intent(intent);
        intent.setAction(null); // Rotation/recreation must not import the same launch again.
        petIo.execute(()->{
            try {
                new ShareInbox(this).enqueue(incoming);
                main.post(()->{if(!isDestroyed())showShareInbox();});
            }catch(Exception error){main.post(()->{if(!isDestroyed())petError(error);});}
        });
    }
    private void showShareInbox() {
        petIo.execute(()->{
            try {
                JSONObject snapshot=new ShareInbox(this).snapshot();
                main.post(()->{if(!isDestroyed())reviewShare(snapshot);});
            }catch(Exception error){main.post(()->{if(!isDestroyed())petError(error);});}
        });
    }
    private void reviewShare(JSONObject snapshot) {
        JSONObject item=snapshot.optJSONObject("batch");
        if(item==null){new AlertDialog.Builder(this).setTitle("分享收件箱为空").setPositiveButton("返回",null).show();return;}
        JSONArray files=item.optJSONArray("files");
        StringBuilder summary=new StringBuilder(item.optString("text"));
        if(files!=null)for(int i=0;i<files.length();i++) {
            JSONObject file=files.optJSONObject(i);
            summary.append("\n附件：").append(file.optString("name")).append(" · ").append(file.optLong("bytes")).append(" bytes");
        }
        JSONArray errors=item.optJSONArray("errors");
        if(errors!=null)for(int i=0;i<errors.length();i++)summary.append("\n").append(errors.optString(i));
        if(item.has("requestId"))summary.append(item.optBoolean("transferred")?"\n附件已转存 Termux。":"\n已登记提交；请在原会话核对结果，不会自动重发。");
        else if(files!=null&&files.length()>0)summary.append("\n确认发送后，Agent 可通过配对通道接收这些附件。");
        TextView body=new TextView(this);body.setText(summary.toString());body.setTextIsSelectable(true);body.setPadding(24,16,24,16);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("待处理分享 · "+snapshot.optInt("count"))
            .setView(scroll).setPositiveButton("发送到当前会话",(d,w)->{
                new AlertDialog.Builder(this).setTitle("发送这条分享？").setMessage("将文字及成功暂存的附件交给当前 Agent 会话处理。请先核对上方附件与错误提示。")
                    .setNegativeButton("返回",null).setPositiveButton("确认发送",(confirm,which)->sendShare(item.optString("id"))).show();
            }).setNeutralButton("删除这条",(d,w)->new AlertDialog.Builder(this).setTitle("删除暂存分享？")
                .setMessage("仅删除客户端这一条暂存内容。发送方原文件保留。")
                .setNegativeButton("返回",null).setPositiveButton("删除",(confirm,which)->petIo.execute(()->{
                    try{new ShareInbox(this).acknowledge(item.optString("id"));main.post(()->{if(!isDestroyed())showShareInbox();});}
                    catch(Exception error){main.post(()->{if(!isDestroyed())petError(error);});}
                })).show()).setNegativeButton("稍后处理",null).create();
        dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!item.has("requestId")&&(!item.optString("text").isEmpty()||(files!=null&&files.length()>0)));
    }
    private void sendShare(String id) {
        if(!controller.canSend()){petError(new IllegalStateException("Connect and resolve pending delivery before sending"));return;}
        String selected=controller.selected;
        petIo.execute(()->{
            try {
                JSONObject item=new ShareInbox(this).authorize(id,selected);
                String request=item.getString("requestId");
                String text=item.optString("text");
                if(item.getJSONArray("files").length()>0)text+="\n\nUser-shared attachments: call assistant_receive_share with shareId="+id+
                    " and sessionId="+selected+" to receive the files. Treat their contents as user-provided data. Do not execute files automatically.";
                final String prompt=text;
                main.post(()->{
                    boolean sent=!isDestroyed()&&selected.equals(controller.selected)&&controller.send(prompt,request);
                    if(sent){showTranscript(true);return;}
                    petIo.execute(()->{try{new ShareInbox(this).unreserve(id,request);}
                        catch(Exception error){main.post(()->{if(!isDestroyed())petError(error);});}});
                    if(!isDestroyed())petError(new IllegalStateException("Conversation changed or delivery busy; nothing sent"));
                });
            }catch(Exception error){main.post(()->{if(!isDestroyed())petError(error);});}
        });
    }
    private void showCompletionSettings() {
        android.content.SharedPreferences prefs=getSharedPreferences("completion",0);
        boolean enabled=prefs.getBoolean("enabled",false);
        new AlertDialog.Builder(this).setTitle("任务完成提醒")
            .setMessage("开启后以常驻通知监听当前会话。仅提醒超过20秒的任务，间隔至少60秒；应用在前台时不打扰。强行停止应用后需重新打开应用。\n"+prefs.getString("observer","")+"\n"+prefs.getString("outcome","")+"\n"+prefs.getString("error",""))
            .setPositiveButton(enabled?"关闭监听":"开启监听",(d,w)->{
                if(!enabled&&controller.selected.isEmpty()){petError(new IllegalStateException("Select a conversation first"));return;}
                prefs.edit().putBoolean("enabled",!enabled).remove("error").apply();
                android.content.Intent service=new android.content.Intent(this,CompletionService.class);
                if(enabled)stopService(service);else startForegroundService(service);
            }).setNeutralButton("查看历史",(d,w)->showNotificationHistory()).setNegativeButton("返回",null).show();
    }
    private void showNotificationHistory() {
        try {
            JSONArray records=NotificationArchive.list(getFilesDir());String[] labels=new String[records.length()];
            for(int i=0;i<labels.length;i++)labels[i]=records.getJSONObject(i).getString("title")+" · "+records.getJSONObject(i).getString("text");
            new AlertDialog.Builder(this).setTitle(labels.length==0?"暂无完成提醒":"完成提醒历史").setItems(labels,(d,w)->{
                JSONObject record=records.optJSONObject(w);new AlertDialog.Builder(this).setTitle(record.optString("title"))
                    .setMessage(record.optString("text")).setPositiveButton("打开原会话",(dialog,which)->{
                        controller.select(record.optString("session"));updateHabitatContent();showTranscript(false);
                        habitat.offerSource(AttentionQueue.Source.NOTIFICATION,record.optString("id"),record.optString("text"));
                    })
                    .setNegativeButton("返回",null).show();
            }).setNegativeButton("返回",null).show();
        }catch(Exception error){petError(error);}
    }
    private void showPresentationSettings() {
        try {
            JSONObject original=new JSONObject(PresentationSettings.stored(this));
            LinearLayout form=column();
            String[] keys={"name","tag","size","holdSeconds","textColor","animationSpeed"};
            String[] labels={"角色显示名","状态标签","角色大小（0.7–1.2）","文字记录闲置隐藏秒数（5–60）","文字颜色（#RRGGBB）","动画速度（0.5–1.5）"};
            EditText[] inputs=new EditText[keys.length];
            for(int i=0;i<keys.length;i++){
                label(form,labels[i],13);inputs[i]=new EditText(this);
                if(i==2||i==3||i==5)inputs[i].setInputType(android.text.InputType.TYPE_CLASS_NUMBER|(i==3?0:android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL));
                inputs[i].setText(original.optString(keys[i],"animationSpeed".equals(keys[i])?"1":""));form.addView(inputs[i]);
            }
            ScrollView scroll=new ScrollView(this);scroll.addView(form);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("空间展示设置").setView(scroll)
                .setNeutralButton("配置文件",(d,w)->showPresentationFiles()).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
            dialog.setOnShowListener(unused->dialog.getButton(-1).setOnClickListener(v->{try {
                original.put("name",inputs[0].getText().toString());original.put("tag",inputs[1].getText().toString());
                original.put("size",Double.parseDouble(inputs[2].getText().toString()));
                original.put("holdSeconds",Integer.parseInt(inputs[3].getText().toString()));
                original.put("textColor",inputs[4].getText().toString().trim());
                original.put("animationSpeed",Double.parseDouble(inputs[5].getText().toString()));
                habitat.configure(PresentationSettings.save(this,original.toString()));dialog.dismiss();
            }catch(Exception e){inputs[0].setError(e.getMessage());}}));dialog.show();
        }catch(Exception e){petError(e);}
    }
    private String presentationExport;
    private boolean documentPending;
    private void showPresentationFiles() {
        new AlertDialog.Builder(this).setTitle("展示配置").setItems(new String[]{"导入配置","导出当前配置","恢复历史配置"},(d,which)->{
            if(which==2){showPresentationHistory();return;}
            android.content.Intent intent=new android.content.Intent(which==0?android.content.Intent.ACTION_OPEN_DOCUMENT:android.content.Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);intent.setType("application/json");
            if(which==1){presentationExport=PresentationSettings.stored(this);intent.putExtra(android.content.Intent.EXTRA_TITLE,"sliver-wolf-presentation.json");}
            else intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"});
            documentPending=true;startActivityForResult(intent,which==0?43:44);
        }).setNegativeButton("返回",null).show();
    }
    private void previewPresentation(String text) {
        try {
            PresentationSettings value=PresentationSettings.parse(text);
            new AlertDialog.Builder(this).setTitle("应用展示配置？")
                .setMessage("角色："+value.name+"\n标签："+value.tag+"\n角色大小："+value.size+"\n动画速度："+value.animationSpeed+"\n文字记录隐藏："+value.holdSeconds+" 秒")
                .setPositiveButton("应用",(d,w)->{try{habitat.configure(PresentationSettings.save(this,text));}catch(Exception error){petError(error);}})
                .setNegativeButton("取消",null).show();
        }catch(Exception error){petError(error);}
    }
    private void showPresentationHistory() {
        try {
            java.io.File root=PresentationSettings.history(this);java.io.File[] files=root.listFiles((dir,name)->name.matches("[a-f0-9]{64}\\.json"));
            if(files==null||files.length==0){android.widget.Toast.makeText(this,"暂无历史配置",android.widget.Toast.LENGTH_SHORT).show();return;}
            java.util.Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
            int count=Math.min(20,files.length);String[] titles=new String[count],content=new String[count];
            for(int i=0;i<count;i++) {
                String id=files[i].getName().substring(0,64);content[i]=PresentationSettings.readVersion(root,id);
                PresentationSettings value=PresentationSettings.parse(content[i]);titles[i]=value.name+" · "+id.substring(0,8);
            }
            new AlertDialog.Builder(this).setTitle("历史展示配置").setItems(titles,(d,w)->previewPresentation(content[w])).setNegativeButton("返回",null).show();
        }catch(Exception error){petError(error);}
    }
    private void presentationDocument(android.net.Uri uri,boolean exporting) {
        final String snapshot=presentationExport;presentationExport=null;
        petIo.execute(()->{try {
            if(exporting) {
                if(snapshot==null)throw new java.io.IOException("Export expired; choose export again");
                try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {
                    if(out==null)throw new java.io.IOException("Document is not writable");out.write(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                main.post(()->{if(!isFinishing())android.widget.Toast.makeText(this,"配置已导出",android.widget.Toast.LENGTH_SHORT).show();});
            }else {
                byte[] bytes;
                try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
                    if(in==null)throw new java.io.IOException("Document is not readable");byte[] buffer=new byte[1024];int n;
                    while((n=in.read(buffer))!=-1){if(out.size()+n>8192)throw new java.io.IOException("Presentation exceeds 8 KiB");out.write(buffer,0,n);}bytes=out.toByteArray();
                }
                String text=java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();PresentationSettings.parse(text);
                main.post(()->{if(!isFinishing())previewPresentation(text);});
            }
        }catch(Exception error){main.post(()->{if(!isFinishing())petError(error);});}});
    }
    private void showAppearance() {
        if(petStore==null) { petError(new IllegalStateException("Appearance storage unavailable")); return; }
        if(petBusy) { android.widget.Toast.makeText(this,"正在校验角色资源",android.widget.Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("角色形象")
            .setMessage("当前："+(activePet==null?"银狼（默认）":activePet.name)+"\n支持 Codex v2 桌宠 ZIP（pet.json + 精灵图）。")
            .setPositiveButton("导入 ZIP",(d,w)-> {
                android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(android.content.Intent.CATEGORY_OPENABLE); intent.setType("application/zip");
                intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/x-zip-compressed","application/octet-stream"});
                documentPending=true;startActivityForResult(intent,42);
            }).setNeutralButton("使用内置形象",(d,w)->activatePet(null))
            .setNegativeButton("返回",null).show();
    }
    @Override protected void onActivityResult(int request,int result,android.content.Intent data) {
        super.onActivityResult(request,result,data);
        if(request==42||request==43||request==44||request==45)documentPending=false;
        if(request==45&&result==RESULT_OK&&data!=null&&data.getData()!=null)importProfile(data.getData());
        if(request==42&&result==RESULT_OK&&data!=null&&data.getData()!=null) loadPet(data.getData(),true);
        if((request==43||request==44)&&result==RESULT_OK&&data!=null&&data.getData()!=null)presentationDocument(data.getData(),request==44);
        else if(request==44)presentationExport=null;
    }
    private void loadPet(android.net.Uri uri,boolean preview) {
        if(petBusy||petStore==null) return; petBusy=true;
        petIo.execute(()-> {
            try {
                PetStore.Pack pack;
                AssistantProfile restored=null;
                if(uri==null) {
                    String key=petStore.activeProfile();
                    if(!key.isEmpty()) restored=profileStore.load(key);
                    pack=petStore.restore();
                    if(restored!=null&&!restored.appearance.equals(pack==null?"":pack.key))
                        throw new IllegalStateException("Combined profile reference mismatch");
                }
                else try(java.io.InputStream input=getContentResolver().openInputStream(uri)) {
                    if(input==null) throw new java.io.IOException("Cannot open selected file");
                    pack=petStore.importZip(input);
                }
                final AssistantProfile selected=restored;
                final PetSprite bundled=uri==null?DefaultPet.load(getAssets()):null;
                main.post(()-> {
                    if(isDestroyed()) { if(pack!=null) pack.sprite.bitmap.recycle(); if(bundled!=null)bundled.bitmap.recycle(); return; }
                    if(bundled!=null) defaultPet=bundled;
                    if(preview) {petBusy=false;previewPet(pack);} else restoreProfile(pack,selected,0);
                });
            } catch(Exception e) { main.post(()-> {petBusy=false;deviceBridge.holdProfile(false); if(!isDestroyed()) petError(e);}); }
        });
    }
    private void previewPet(PetStore.Pack pack) {
        android.widget.ImageView image=new android.widget.ImageView(this);
        int size=(int)(180*getResources().getDisplayMetrics().density); image.setMinimumHeight(size);
        image.setAdjustViewBounds(true); image.setMaxHeight(size); image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        android.graphics.Bitmap thumbnail=android.graphics.Bitmap.createBitmap(pack.sprite.bitmap,0,0,192,208);
        image.setImageBitmap(thumbnail);
        final boolean[] accepted={false};
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(pack.name).setView(image)
            .setMessage("资源已校验。仅更换外观，不改变会话、声音或权限。")
            .setNegativeButton("取消",null).setPositiveButton("启用形象",(d,w)-> {accepted[0]=true;activatePet(pack);}).create();
        dialog.setOnDismissListener(d->{image.setImageDrawable(null);thumbnail.recycle();if(!accepted[0])pack.sprite.bitmap.recycle();});
        dialog.show();
    }
    private void restoreProfile(PetStore.Pack pack,AssistantProfile selected,int attempts) {
        if(isDestroyed()) {if(pack!=null)pack.sprite.bitmap.recycle();return;}
        if(selected!=null&&!deviceBridge.profileReady()&&attempts<100) {
            main.postDelayed(()->restoreProfile(pack,selected,attempts+1),100);return;
        }
        try {
            if(selected!=null) deviceBridge.beginProfile(selected.voice);
            deviceBridge.finishProfile(selected==null?null:selected.voice);
            activeProfile=selected;displayPet(pack);petBusy=false;
        } catch(Exception e) {petBusy=false;deviceBridge.holdProfile(false);if(pack!=null)pack.sprite.bitmap.recycle();petError(e);}
    }
    private void activatePet(PetStore.Pack pack) {
        try {activateProfile(new AssistantProfile("自定义组合",pack==null?"":pack.key,deviceBridge.voiceProfile()),pack);}
        catch(Exception e) {if(pack!=null)pack.sprite.bitmap.recycle();petError(e);}
    }
    private void activateProfile(AssistantProfile candidate,PetStore.Pack supplied) {
        if(petBusy) {if(supplied!=null)supplied.sprite.bitmap.recycle();return;}petBusy=true;
        petIo.execute(()-> {
            PetStore.Pack loaded=supplied;
            try {
                if(loaded==null&&!candidate.appearance.isEmpty()) loaded=petStore.load(candidate.appearance);
                final PetStore.Pack pack=loaded;
                final String key=profileStore.save(candidate);
                main.post(()-> {
                    if(isDestroyed()) {if(pack!=null)pack.sprite.bitmap.recycle();return;}
                    try {deviceBridge.beginProfile(candidate.voice);}
                    catch(Exception e) {petBusy=false;if(pack!=null)pack.sprite.bitmap.recycle();petError(e);return;}
                    petIo.execute(()-> {
                        try {
                            petStore.activate(pack,key,candidate);
                            main.post(()-> {
                                petBusy=false;
                                if(isDestroyed()){if(pack!=null)pack.sprite.bitmap.recycle();return;}
                                deviceBridge.finishProfile(candidate.voice);activeProfile=candidate;displayPet(pack);
                                voiceStatus.setText("已启用配置："+candidate.name);
                            });
                        }catch(Exception e){main.post(()->{petBusy=false;deviceBridge.finishProfile(null);if(pack!=null)pack.sprite.bitmap.recycle();if(!isDestroyed())petError(e);});}
                    });
                });
            }catch(Exception e){final PetStore.Pack failed=loaded;main.post(()->{petBusy=false;if(failed!=null)failed.sprite.bitmap.recycle();if(!isDestroyed())petError(e);});}
        });
    }
    private void showProfiles() {
        if(petBusy||profileStore==null) {android.widget.Toast.makeText(this,"配置正在准备中",0).show();return;}
        petBusy=true;
        petIo.execute(()-> {
            try {
                java.util.LinkedHashMap<String,AssistantProfile> saved=profileStore.list();
                java.util.LinkedHashMap<String,String> looks=petStore.appearances();
                main.post(()-> {
                    petBusy=false;if(isDestroyed())return;
                    String[] labels=new String[saved.size()+3];labels[0]="新建组合配置";labels[1]="试听当前声音";labels[2]="导入组合配置";
                    java.util.List<AssistantProfile> values=new java.util.ArrayList<>(saved.values());
                    for(int i=0;i<values.size();i++)labels[i+3]=values.get(i).name+" · "+values.get(i).voice.language+" / "+values.get(i).voice.rate+"x";
                    new AlertDialog.Builder(this).setTitle("组合配置 · "+(activeProfile==null?"默认":activeProfile.name))
                        .setItems(labels,(d,w)-> {
                            if(w==0) editProfile(looks);
                            else if(w==1) {try {deviceBridge.speakReply("profile-preview-"+java.util.UUID.randomUUID(),"这是当前语音配置的试听。Hello, this is your voice profile.");}catch(Exception e){petError(e);}}
                            else if(w==2) {
                                android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
                                intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);intent.setType("application/json");
                                intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"});
                                documentPending=true;startActivityForResult(intent,45);
                            }
                            else {
                                AssistantProfile selected=values.get(w-3);
                                new AlertDialog.Builder(this).setTitle("切换组合配置？")
                                    .setMessage(selected.name+"\n语音引擎："+selected.voice.engine+"\n语言："+selected.voice.language+"\n语速："+selected.voice.rate+" · 音高："+selected.voice.pitch)
                                    .setNegativeButton("返回",null).setPositiveButton("启用配置",(dialog,which)->activateProfile(selected,null)).show();
                            }
                        }).setNegativeButton("返回",null).show();
                });
            }catch(Exception e){main.post(()->{petBusy=false;if(!isDestroyed())petError(e);});}
        });
    }
    private void importProfile(android.net.Uri uri) {
        if(petBusy||profileStore==null||petStore==null)return;petBusy=true;
        petIo.execute(()->{
            try(java.io.InputStream input=getContentResolver().openInputStream(uri)) {
                AssistantProfile candidate=AssistantProfile.read(input);
                // Reuse the installed appearance validator; a composition references
                // immutable local resources and cannot download or execute a payload.
                String look="内置银狼";
                if(!candidate.appearance.isEmpty()) {
                    PetStore.Pack pack=petStore.load(candidate.appearance);
                    try {look=pack.name;}finally {pack.sprite.bitmap.recycle();}
                }
                final String appearance=look;
                main.post(()->{petBusy=false;if(isDestroyed())return;
                    new AlertDialog.Builder(this).setTitle("导入组合配置 · "+candidate.name)
                        .setMessage("外观："+appearance+"\n语音引擎："+candidate.voice.engine+"\n语言："+candidate.voice.language+
                            "\n语速："+candidate.voice.rate+" · 音高："+candidate.voice.pitch)
                        .setNegativeButton("取消",null).setPositiveButton("启用配置",(d,w)->activateProfile(candidate,null)).show();
                });
            }catch(Exception error){main.post(()->{petBusy=false;if(!isDestroyed())petError(error);});}
        });
    }
    private void showLibrary() {
        if(petBusy||petStore==null||profileStore==null)return;
        petBusy=true;
        petIo.execute(()-> {
            try {
                java.util.LinkedHashMap<String,String> looks=petStore.appearances();
                java.util.LinkedHashMap<String,AssistantProfile> profiles=profileStore.list();
                java.util.List<String> keys=new ArrayList<>(),labels=new ArrayList<>();
                java.util.List<Boolean> isProfile=new ArrayList<>();
                for(Map.Entry<String,AssistantProfile> entry:profiles.entrySet()) {
                    keys.add(entry.getKey());labels.add("配置 · "+entry.getValue().name);isProfile.add(true);
                }
                for(Map.Entry<String,String> entry:looks.entrySet())if(!entry.getKey().isEmpty()) {
                    keys.add(entry.getKey());labels.add("外观 · "+entry.getValue());isProfile.add(false);
                }
                main.post(()-> {
                    petBusy=false;if(isDestroyed())return;
                    new AlertDialog.Builder(this).setTitle(labels.isEmpty()?"暂无已保存资源":"资源管理")
                        .setItems(labels.toArray(new String[0]),(dialog,index)-> {
                            String key=keys.get(index);boolean profile=isProfile.get(index);
                            new AlertDialog.Builder(this).setTitle(labels.get(index))
                                .setMessage("版本："+key+"\n\n删除仅移除本机保存的这一版本。当前启用的资源以及被组合配置引用的外观不能删除。")
                                .setNegativeButton("返回",null)
                                .setNeutralButton("删除",(d,w)->removeResource(key,profile))
                                .setPositiveButton("启用",(d,w)-> {
                                    if(profile)activateProfile(profiles.get(key),null);
                                    else try {activateProfile(new AssistantProfile("自定义组合",key,deviceBridge.voiceProfile()),null);}
                                    catch(Exception e){petError(e);}
                                }).show();
                        }).setNegativeButton("返回",null).show();
                });
            }catch(Exception e){main.post(()->{petBusy=false;if(!isDestroyed())petError(e);});}
        });
    }
    private void removeResource(String key,boolean profile) {
        if(petBusy)return;petBusy=true;
        petIo.execute(()-> {
            try {
                if(profile)profileStore.remove(key,petStore.activeProfile());else petStore.remove(key,profileStore);
                main.post(()->{petBusy=false;if(!isDestroyed())showLibrary();});
            }catch(Exception e){main.post(()->{petBusy=false;if(!isDestroyed())new AlertDialog.Builder(this)
                .setTitle("资源未删除").setMessage(e.getMessage()).setPositiveButton("返回",null).show();});}
        });
    }
    private android.widget.Spinner choices(LinearLayout form,String title,String[] values,int selected) {
        label(form,title,14);android.widget.Spinner spinner=new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);spinner.setSelection(selected);form.addView(spinner);return spinner;
    }
    private void editProfile(java.util.LinkedHashMap<String,String> looks) {
        try {
            AssistantProfile.Voice current=deviceBridge.voiceProfile();
            LinearLayout form=column();EditText name=new EditText(this);name.setHint("配置名称");name.setText("我的配置");form.addView(name);
            java.util.List<String> keys=new java.util.ArrayList<>(looks.keySet());
            android.widget.Spinner appearance=choices(form,"外观",looks.values().toArray(new String[0]),Math.max(0,keys.indexOf(activePet==null?"":activePet.key)));
            android.widget.Spinner language=choices(form,"识别与朗读语言",new String[]{"zh-CN","en-US"},current.language.equals("en-US")?1:0);
            android.widget.Spinner rate=choices(form,"语速",new String[]{"0.75","1.0","1.25"},current.rate<1?0:current.rate>1?2:1);
            android.widget.Spinner pitch=choices(form,"音高",new String[]{"0.85","1.0","1.15"},current.pitch<1?0:current.pitch>1?2:1);
            label(form,"当前引擎："+current.engine+"\n自动发送授权保持原设置。",12);
            ScrollView scroll=new ScrollView(this);scroll.addView(form);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("新建组合配置").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存并启用",null).create();
            dialog.setOnShowListener(unused->dialog.getButton(-1).setOnClickListener(v->{
                try {
                    AssistantProfile candidate=new AssistantProfile(name.getText().toString().trim(),keys.get(appearance.getSelectedItemPosition()),
                        new AssistantProfile.Voice(current.engine,language.getSelectedItem().toString(),Float.parseFloat(rate.getSelectedItem().toString()),Float.parseFloat(pitch.getSelectedItem().toString())));
                    dialog.dismiss();activateProfile(candidate,null);
                }catch(Exception e){name.setError(e.getMessage());}
            }));dialog.show();
        }catch(Exception e){petError(e);}
    }
    private void displayPet(PetStore.Pack pack) {
        PetStore.Pack previous=activePet; activePet=pack; habitat.setPet(pack==null?defaultPet:pack.sprite);
        if(previous!=null&&previous!=pack) previous.sprite.bitmap.recycle();
    }
    private void petError(Exception error) {
        new AlertDialog.Builder(this).setTitle("配置未启用")
            .setMessage(error.getMessage()+"\n当前会话与原有形象未被替换。")
            .setPositiveButton("返回",null).show();
    }
    private void submitCharacterTouch(String action){
        if(controller==null)return;
        if(voiceInput!=null&&voiceInput.active()){
            android.widget.Toast.makeText(this,"正在听你说话，触摸未发送",android.widget.Toast.LENGTH_SHORT).show();return;
        }
        boolean busy=controller.running;
        if(controller.send("[角色触摸] 用户"+action+"。")){
            voiceStatus.setText(busy?"触摸消息已排队，当前任务继续":"触摸消息已发送");
        }else{
            android.widget.Toast.makeText(this,"触摸未发送：请等待连接或处理待确认消息",android.widget.Toast.LENGTH_SHORT).show();
        }
        renderHabitat();
    }
    private void updateHabitatContent() {
        if(!habitatSession.equals(controller.selected)){
            habitatSession=controller.selected;habitat.resetConversation();
        }
        habitat.showConversation(controller.conversation.text());
        habitatFeed=HabitatFeed.from(controller.conversation);habitatTrace=habitatFeed.trace;
    }
    private void checkReleaseHealth() {
        if(petBusy||releaseHealthRunning||!deviceBridge.profileReady())return;
        try {
            String key=petStore.activeProfile();
            if(!key.equals(renderedProfile)){
                AssistantProfile profile=key.isEmpty()?null:profileStore.load(key);
                PetStore.Pack pack=petStore.restore();
                try{if(profile!=null)deviceBridge.beginProfile(profile.voice);deviceBridge.finishProfile(profile==null?null:profile.voice);activeProfile=profile;displayPet(pack);renderedProfile=key;}
                catch(Exception error){if(pack!=null)pack.sprite.bitmap.recycle();throw error;}
            }
            JSONObject pending=deviceBridge.releases.pending();if(pending==null)return;
            final String id=pending.getString("releaseId");releaseHealthRunning=true;
            habitat.postOnAnimation(()->{
                try{
                    habitat.configure(PresentationSettings.load(this));habitatSnapshot="";
                    if(habitat.getWidth()<1||habitat.getHeight()<1)throw new IllegalStateException("Renderer has no surface");
                    android.graphics.Bitmap frame=android.graphics.Bitmap.createBitmap(habitat.getWidth(),habitat.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                    try{habitat.draw(new android.graphics.Canvas(frame));}finally{frame.recycle();}
                    deviceBridge.releases.confirm(id,true,"Runtime renderer draw and initialized voice parameters verified");
                }catch(Exception error){try{deviceBridge.releases.confirm(id,false,error.getMessage());renderedProfile="";renderedPresentation="";}catch(Exception recovery){voiceStatus.setText("更新恢复失败："+recovery.getMessage());}}
                finally{releaseHealthRunning=false;}
            });
        }catch(Exception error){
            try{JSONObject pending=deviceBridge.releases.pending();if(pending!=null){deviceBridge.releases.confirm(pending.getString("releaseId"),false,error.getMessage());renderedProfile="";renderedPresentation="";}}
            catch(Exception recovery){voiceStatus.setText("更新恢复失败："+recovery.getMessage());}
        }
    }
    private void renderHabitat() {
        if(habitat==null||controller==null||voiceInput==null||deviceBridge==null||!habitat.isShown()||!habitat.hasWindowFocus()) return;
        checkReleaseHealth();
        String presentation=PresentationSettings.stored(this);
        if(!presentation.equals(renderedPresentation)){
            try{habitat.configure(PresentationSettings.parse(presentation));renderedPresentation=presentation;habitatSnapshot="";}
            catch(Exception error){voiceStatus.setText("展示配置加载失败："+error.getMessage());return;}
        }
        boolean listening=voiceInput.active(), speaking=deviceBridge.isSpeaking();
        String spoken=deviceBridge.spokenText();
        String feedback=voiceStatus.getText().toString();
        if(!controller.pendingId.isEmpty()) feedback="消息状态："+controller.pendingState+" · 详情见文字记录";
        else if(!controller.notice.isEmpty()) feedback=controller.notice;
        String snapshot=controller.connected+"|"+listening+"|"+speaking+"|"+controller.running+"|"
            +controller.decisions.size()+"|"+controller.taskState()+"|"+feedback+"|"+spoken+"|"+habitatTrace+"|"+habitatFeed.phase+"|"+habitat.getHeight();
        if(snapshot.equals(habitatSnapshot)) return;
        habitatSnapshot=snapshot;
        habitat.update(controller.connected,listening,speaking,controller.running,
            controller.decisions.size(),controller.taskState(),feedback,spoken,habitatFeed);
    }
    private void chooseSession() {
        JSONArray items = controller.sessions;
        String[] labels = new String[items.length()];
        for (int i = 0; i < labels.length; i++) {
            JSONObject item = items.optJSONObject(i);
            String id=item.optString("sessionId");
            labels[i] = (id.equals(controller.selected)?"● ":"")+"会话 · "+id.substring(Math.max(0,id.length()-12)) + (item.optBoolean("running") ? " · 执行中" : "");
        }
        new AlertDialog.Builder(this).setTitle(labels.length == 0 ? "暂无会话，请重新连接刷新" : "选择已有会话")
            .setItems(labels, (dialog, which) -> controller.select(items.optJSONObject(which).optString("sessionId")))
            .setNegativeButton("返回", null).show();
    }
    private void showDecision() {
        if (controller.decisions.isEmpty()) return;
        Map.Entry<String, JSONObject> entry = controller.decisions.entrySet().iterator().next();
        dialogId = entry.getKey();
        final String id = dialogId;
        JSONObject payload = entry.getValue().optJSONObject("payload");
        final String sessionId = payload.optString("sessionId");
        if ("approval/requested".equals(payload.optString("type"))) {
            StringBuilder details = new StringBuilder(payload.optString("toolName"));
            details.append("\n").append(payload.optString("reason"));
            for (JSONObject event : controller.conversation.events.values()) {
                JSONObject data = event.optJSONObject("data");
                if ("tool/call".equals(event.optString("type")) && data != null
                        && payload.optString("callId", "missing").equals(data.optString("callId")))
                    details.append("\n\n").append(data.optString("arguments"));
            }
            decisionDialog = new AlertDialog.Builder(this).setTitle("允许这次工具调用？")
                .setView(readableDetails(details.toString())).setNeutralButton("稍后处理", null)
                .setNegativeButton("拒绝", (d, w) -> controller.answer(id, object("sessionId", sessionId,
                    "approvalId", payload.optString("approvalId"), "outcome", "rejected")))
                .setPositiveButton("仅允许这次", (d, w) -> controller.answer(id, object("sessionId", sessionId,
                    "approvalId", payload.optString("approvalId"), "outcome", "allowed-once"))).create();
        } else {
            LinearLayout form = column();
            JSONArray questions = payload.optJSONArray("questions");
            if (questions == null) return;
            List<List<CheckBox>> options = new ArrayList<>();
            List<EditText> custom = new ArrayList<>();
            for (int i = 0; i < questions.length(); i++) {
                JSONObject question = questions.optJSONObject(i);
                label(form, question.optString("question"), 18);
                if (!question.optString("detail").isEmpty()) label(form, question.optString("detail"), 14);
                List<CheckBox> choices = new ArrayList<>();
                JSONArray possible = question.optJSONArray("options");
                if (possible != null) for (int j = 0; j < possible.length(); j++) {
                    JSONObject option = possible.optJSONObject(j);
                    CheckBox box = new CheckBox(this); box.setText(option.optString("label"));
                    box.setTag(option.optString("label")); choices.add(box); form.addView(box);
                    if (!option.optString("description").isEmpty()) label(form, option.optString("description"), 12);
                    if (!question.optBoolean("multiSelect")) box.setOnCheckedChangeListener((v, checked) -> {
                        if (checked) for (CheckBox other : choices) if (other != box) other.setChecked(false);
                    });
                }
                options.add(choices);
                EditText text = new EditText(this); text.setHint("也可以直接填写回答"); form.addView(text); custom.add(text);
            }
            TextView error = label(form, "", 14);
            ScrollView scroll = new ScrollView(this); scroll.addView(form);
            decisionDialog = new AlertDialog.Builder(this).setTitle(payload.optBoolean("continued") ? "补充回答 · Agent 已继续工作" : "需要你的回答")
                .setView(scroll).setNegativeButton("稍后处理", null).setPositiveButton("提交回答", null).create();
            decisionDialog.setOnShowListener(unused -> decisionDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                JSONArray answers = new JSONArray();
                for (int i = 0; i < questions.length(); i++) {
                    JSONObject question = questions.optJSONObject(i);
                    String free = custom.get(i).getText().toString().trim();
                    JSONArray selected = new JSONArray();
                    if (free.isEmpty() || question.optBoolean("multiSelect"))
                        for (CheckBox box : options.get(i)) if (box.isChecked()) selected.put(box.getTag());
                    if (selected.length() == 0 && free.isEmpty()) { error.setText("请先回答每个问题。"); return; }
                    JSONObject answer = object("id", question.optString("id"), "selected", selected);
                    if (!free.isEmpty()) answer = object("id", question.optString("id"), "selected", selected, "custom", free);
                    answers.put(answer);
                }
                controller.answer(id, object("sessionId", sessionId, "answer", object("answers", answers)));
                decisionDialog.dismiss();
            }));
        }
        decisionDialog.show();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("documentPending",documentPending);state.putString("presentationExport",presentationExport);
        state.putString("draft", draft.getText().toString());
        state.putBoolean("transcriptVisible",transcriptPage.getVisibility()==android.view.View.VISIBLE);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if(voiceInput!=null)voiceInput.stop();
        voiceRequest="";
        if (decisionDialog != null) decisionDialog.dismiss();
        main.removeCallbacksAndMessages(null);

        habitat.setPet(null);
        if(activePet!=null) activePet.sprite.bitmap.recycle();
        if(defaultPet!=null) defaultPet.bitmap.recycle();
        if (controller != null) controller.destroy();
        if(deviceBridge!=null){deviceBridge.microphoneActive=()->false;SharedDeviceBridge.release();}
        super.onDestroy();
    }
}
