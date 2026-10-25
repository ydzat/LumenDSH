package local.sliverwolf.assistant;

import org.json.JSONObject;

/** Read-only view of exact backend metadata; unknown capacity stays unknown. */
final class ModelSettings {
    private static String value(JSONObject data,String key) {
        return data.isNull(key)?"未知（DSH 未提供）":data.optString(key,"未知");
    }
    static String describe(JSONObject data,String error) {
        if(data==null)return error;
        return "跟随当前会话的 DSH 模型\n\n"+value(data,"provider")+" / "+value(data,"model")+
            "\n上下文容量："+value(data,"contextWindow")+" tokens"+
            "\n输入类型："+value(data,"inputModalities")+
            "\n推理强度："+value(data,"reasoningEffort")+
            "\n默认输出："+value(data,"defaultOutputTokens")+
            "\n输出硬上限："+value(data,"maxOutputTokens")+
            "\n自主活动预算："+value(data,"userOutputBudget")+
            "\n按模型默认值计算的自主输出上限："+value(data,"autonomousOutputCeiling")+
            "\n同模型最近请求的输出配置："+value(data,"lastRequestOutputTokens")+
            "\n\n输入裁剪、上下文压缩及阈值由 DSH 原生管理。自主步骤会结合当次请求配置重新计算输出上限，普通聊天不受自主预算限制。"+
            "\n本地向量模型、声纹及任务次数/时间预算独立配置。\n更新时间："+value(data,"observedAt");
    }
}
