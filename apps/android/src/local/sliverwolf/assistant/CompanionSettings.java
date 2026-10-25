package local.sliverwolf.assistant;

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
    private void render(JSONObject state) {
        if(activity.isDestroyed())return;
        try {
            JSONObject policy=state.getJSONObject("policy");
            LinearLayout form=new LinearLayout(activity);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(32,16,32,16);
            TextView context=new TextView(activity);context.setText("沿用当前会话。仅整理本地记忆与有来源的联想，不创建另一个 Agent。\n角色名称沿用空间展示设置。\n"+
                activity.getSharedPreferences("device-tools",0).getString("behaviorSync","名称与会话同步待检查"));form.addView(context);
            CheckBox enabled=check(form,"启用自主活动",policy.getBoolean("enabled"));
            CheckBox paused=check(form,"暂停全部自主活动",policy.getBoolean("paused"));
            EditText style=field(form,"表达与互动偏好",policy.getString("style"));
            JSONArray windows=policy.getJSONArray("windows");StringBuilder times=new StringBuilder();
            for(int i=0;i<windows.length();i++){JSONObject w=windows.getJSONObject(i);if(i>0)times.append('\n');times.append(w.getString("start")).append('-').append(w.getString("end")).append(' ').append(w.getInt("wakes"));}
            EditText windowInput=field(form,"活动窗口（每行：09:00-22:00 2）",times.toString());
            EditText idle=field(form,"空闲多久后可活动（分钟）",policy.getString("idleMinutes"));
            EditText gap=field(form,"活动最小间隔（分钟）",policy.getString("minGapMin"));
            EditText skip=field(form,"随机休息概率（0～1）",policy.getString("skipChance"));
            JSONArray actions=policy.getJSONArray("activities");
            CheckBox consolidate=check(form,"整理已有记忆",actions.toString().contains("consolidate"));
            CheckBox reflect=check(form,"回顾共同经历与联想",actions.toString().contains("reflect"));
            JSONObject budget=policy.getJSONObject("budget");
            LinkedHashMap<String,EditText> limits=new LinkedHashMap<>();
            String[][] labels={{"dailyRuns","每天最多活动次数"},{"steps","每次最多模型步骤"},{"toolCalls","每次最多工具调用"},
                {"writes","每次最多记忆写入"},{"outputTokens","每个模型步骤最多输出 token"},{"seconds","每次最长时间（秒）"}};
            for(String[] item:labels)limits.put(item[0],field(form,item[1],budget.getString(item[0])));
            TextView status=new TextView(activity);status.setText(history(state));status.setTextIsSelectable(true);form.addView(status);
            ScrollView scroll=new ScrollView(activity);scroll.addView(form);
            AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("自主活动与行为").setView(scroll)
                .setNegativeButton("返回",null).setNeutralButton("历史版本",(d,w)->versions(state)).setPositiveButton("保存",null).create();
            dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try {
                    JSONObject changed=new JSONObject(policy.toString());
                    changed.put("agentName",PresentationSettings.load(activity).name);changed.put("sessionId",session);
                    changed.put("enabled",enabled.isChecked());changed.put("paused",paused.isChecked());changed.put("style",style.getText().toString());
                    changed.put("idleMinutes",Integer.parseInt(idle.getText().toString()));changed.put("minGapMin",Integer.parseInt(gap.getText().toString()));
                    changed.put("skipChance",Double.parseDouble(skip.getText().toString()));
                    JSONArray nextWindows=new JSONArray();
                    for(String line:windowInput.getText().toString().trim().split("\\n")) {
                        java.util.regex.Matcher m=java.util.regex.Pattern.compile("^(\\d{1,2}:\\d{2})-(\\d{1,2}:\\d{2})\\s+(\\d+)$").matcher(line.trim());
                        if(!m.matches())throw new IllegalArgumentException("活动窗口格式应为 09:00-22:00 2");
                        nextWindows.put(new JSONObject().put("start",m.group(1)).put("end",m.group(2)).put("wakes",Integer.parseInt(m.group(3))));
                    }
                    changed.put("windows",nextWindows);JSONArray nextActions=new JSONArray();
                    if(consolidate.isChecked())nextActions.put("consolidate");if(reflect.isChecked())nextActions.put("reflect");changed.put("activities",nextActions);
                    JSONObject nextBudget=new JSONObject();for(String key:limits.keySet())nextBudget.put(key,Integer.parseInt(limits.get(key).getText().toString()));
                    changed.put("budget",nextBudget);
                    JSONObject request=new JSONObject().put("policy",changed).put("expectedRevision",state.getString("revision"));
                    io.execute(()->{try{JSONObject saved=transport.companion("policy",request);activity.runOnUiThread(()->{dialog.dismiss();render(saved);});}catch(Exception e){error(e);}});
                }catch(Exception e){error(e);}
            }));dialog.show();
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
