package local.sliverwolf.web;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.*;
import org.json.*;
import java.util.LinkedHashMap;
import java.util.concurrent.Executor;

/** Secondary controls for the existing DSH companion policy, never a second scheduler. */
final class CompanionSettings {
    private final Activity activity;
    private final Executor io;
    private final String session;
    private final DshTransport transport;
    CompanionSettings(Activity activity,Executor io,String session) {
        this.activity=activity;this.io=io;this.session=session;
        transport=new DshTransport(activity.getSharedPreferences("session",0));
    }
    void show() {io.execute(()->{try{JSONObject state=transport.companion("status",null);activity.runOnUiThread(()->render(state));}catch(Exception e){error(e);}});}
    private void error(Exception e) {activity.runOnUiThread(()->{if(!activity.isDestroyed())new AlertDialog.Builder(activity).setTitle("自主活动")
        .setMessage("操作未完成："+e.getMessage()).setPositiveButton("返回",null).show();});}
    private EditText field(LinearLayout form,String label,String value) {
        TextView title=new TextView(activity);title.setText(label);form.addView(title);
        EditText input=new EditText(activity);input.setText(value);form.addView(input);return input;
    }
    private CheckBox check(LinearLayout form,String label,boolean value) {
        CheckBox box=new CheckBox(activity);box.setText(label);box.setChecked(value);form.addView(box);return box;
    }
    private SettingsDrawer drawer;
    private JSONObject draft,state;
    private void render(JSONObject state) {
        if(activity.isDestroyed())return;
        try {this.state=state;draft=new JSONObject(state.getJSONObject("policy").toString());
            if(drawer!=null)drawer.dismiss();drawer=new SettingsDrawer(activity,()->{});drawer.open(this::mainPage);
        }catch(Exception e){error(e);}
    }
    private void put(JSONObject object,String key,Object value){try{object.put(key,value);}catch(Exception e){error(e);}}
    private void textInput(String label,JSONObject object,String key,boolean numeric){
        EditText edit=drawer.input(label,object.optString(key),!numeric);
        if(numeric)edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edit.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){put(object,key,s.toString());}public void afterTextChanged(android.text.Editable e){}});
    }
    private void mainPage(){
        drawer.title("行为与自主活动");drawer.note("设置应用到当前 Agent 与当前会话。");
        drawer.toggle("启用自主活动","总开关；关闭后不再安排任何新的自主活动",draft.optBoolean("enabled"),on->put(draft,"enabled",on));
        boolean paused=draft.optBoolean("paused");
        drawer.row(paused?"当前：已暂停 · 点按恢复":"当前：运行中 · 点按暂停",paused?"暂停期间保留原有安排，恢复后继续":"下次唤醒会按活动时间与预算执行",()->{put(draft,"paused",!paused);drawer.refresh();});
        textInput("表达与互动偏好",draft,"style",false);
        drawer.row("活动时间","设置允许活动的时间段与次数",()->drawer.go(this::windowsPage));
        drawer.section("允许的活动");
        for(String key:new String[]{"consolidate","reflect","develop"}){
            String label=key.equals("consolidate")?"整理已有记忆":key.equals("reflect")?"回顾共同经历与联想":"受控自主开发（agent/dev 构建与提交）";
            drawer.toggle(label,"",contains(draft.optJSONArray("activities"),key),on->{
                JSONArray next=new JSONArray();for(String candidate:new String[]{"consolidate","reflect","develop"})if(candidate.equals(key)?on:contains(draft.optJSONArray("activities"),candidate))next.put(candidate);put(draft,"activities",next);});
        }
        drawer.row("高级活动限制","空闲时间、频率、模型与工具预算",()->drawer.go(this::advancedPage));
        drawer.row("最近活动","查看实际执行记录",()->drawer.go(()->{drawer.title("最近活动");try{drawer.note(history(state));}catch(Exception e){error(e);}}));
        drawer.row("历史版本","恢复已保存的行为配置",()->versions(state));
        drawer.row("保存设置","验证并应用到 DSH",this::save);
    }
    private boolean contains(JSONArray array,String value){if(array!=null)for(int i=0;i<array.length();i++)if(value.equals(array.optString(i)))return true;return false;}
    private void windowsPage(){
        drawer.title("活动时间");JSONArray windows=draft.optJSONArray("windows");
        for(int i=0;i<windows.length();i++){
            final int index=i;JSONObject window=windows.optJSONObject(i);drawer.section("时间段 "+(i+1));
            for(String key:new String[]{"start","end"})drawer.row(key.equals("start")?"开始时间":"结束时间",window.optString(key),()->{
                String[] parts=window.optString(key).split(":");new android.app.TimePickerDialog(activity,(picker,h,m)->{put(window,key,String.format(java.util.Locale.ROOT,"%02d:%02d",h,m));drawer.refresh();},Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),true).show();});
            textInput("最多活动次数",window,"wakes",true);
            drawer.row("移除此时间段","",()->{windows.remove(index);drawer.refresh();});
        }
        drawer.row("添加时间段","09:00–22:00",()->{try{windows.put(new JSONObject().put("start","09:00").put("end","22:00").put("wakes",2));drawer.refresh();}catch(Exception e){error(e);}});
    }
    private void advancedPage(){
        drawer.title("高级活动限制");
        textInput("空闲多久后可活动（分钟）",draft,"idleMinutes",true);textInput("最小间隔（分钟）",draft,"minGapMin",true);textInput("随机休息概率（0–1）",draft,"skipChance",true);
        JSONObject budget=draft.optJSONObject("budget");String[][] labels={{"dailyRuns","每天最多活动次数"},{"steps","每次最多模型步骤"},{"toolCalls","每次最多工具调用"},{"writes","每次最多记忆写入"},{"outputTokens","每个模型步骤最多输出 token"},{"seconds","每次最长时间（秒）"}};
        for(String[] item:labels)textInput(item[1],budget,item[0],true);
    }
    private void save(){
        try{
            JSONObject changed=new JSONObject(draft.toString());changed.put("agentName",PresentationSettings.load(activity).name);changed.put("sessionId",session);
            for(String key:new String[]{"idleMinutes","minGapMin"})changed.put(key,Integer.parseInt(changed.optString(key)));
            changed.put("skipChance",Double.parseDouble(changed.optString("skipChance")));
            JSONObject budget=changed.getJSONObject("budget");java.util.Iterator<String> keys=budget.keys();while(keys.hasNext()){String key=keys.next();budget.put(key,Integer.parseInt(budget.optString(key)));}
            JSONArray windows=changed.getJSONArray("windows");for(int i=0;i<windows.length();i++){JSONObject window=windows.getJSONObject(i);window.put("wakes",Integer.parseInt(window.optString("wakes")));}
            JSONObject request=new JSONObject().put("policy",changed).put("expectedRevision",state.getString("revision"));
            io.execute(()->{try{JSONObject saved=transport.companion("policy",request);activity.runOnUiThread(()->render(saved));}catch(Exception e){error(e);}});
        }catch(Exception e){error(e);}
    }
    private String history(JSONObject state)throws Exception {
        StringBuilder text=new StringBuilder("\n最近活动\n");JSONArray runs=state.getJSONArray("runs");
        for(int i=runs.length()-1;i>=Math.max(0,runs.length()-10);i--){JSONObject r=runs.getJSONObject(i);
            text.append(r.optString("startedAt")).append(" · ").append(r.optString("activity")).append("\n")
                .append(r.optString("status")).append(" · 写入尝试 ").append(r.optInt("writesAttempted")).append("\n");}
        if(runs.length()==0)text.append("尚未执行自主活动\n");
        if(!state.isNull("lastError"))text.append("执行错误：").append(state.optString("lastError"));
        return text.toString();
    }
    private void versions(JSONObject state) {
        try {
            JSONArray versions=state.getJSONArray("versions");String[] rows=new String[versions.length()];for(int i=0;i<rows.length;i++)rows[i]=versions.getString(i);
            new AlertDialog.Builder(activity).setTitle("恢复行为版本").setItems(rows,(d,index)->{
                io.execute(()->{try{JSONObject restored=transport.companion("restore",new JSONObject().put("revision",rows[index]).put("expectedRevision",state.getString("revision")));
                    activity.runOnUiThread(()->render(restored));}catch(Exception e){error(e);}});
            }).setNegativeButton("返回",null).show();
        }catch(Exception e){error(e);}
    }
}
