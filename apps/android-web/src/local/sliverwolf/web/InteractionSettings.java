package local.sliverwolf.web;

import android.content.Context;
import android.content.SharedPreferences;

/** Interaction policy is separate from immutable appearance and speech profiles. */
final class InteractionSettings {
    final SharedPreferences values;
    InteractionSettings(Context context) {
        values=context.getSharedPreferences("interaction",0);
        if(!values.contains("version")) {
            SharedPreferences legacy=context.getSharedPreferences("voice",0);
            values.edit().putBoolean("homeAutoSend",true)
                .putBoolean("chatAutoSend",legacy.getBoolean("autoSend",false))
                .putString("homeReply","voice").putString("chatReply","follow")
                .putInt("version",1).apply();
        }
    }
    boolean autoSend(boolean home){return values.getBoolean(home?"homeAutoSend":"chatAutoSend",home);}
    boolean homeWake(){return values.getBoolean("homeWake",false);}
    boolean speak(boolean home,boolean voice) {
        String mode=values.getString(home?"homeReply":"chatReply",home?"voice":"follow");
        return "voice".equals(mode)||("follow".equals(mode)&&voice);
    }
    String language(String fallback){return values.getString("inputLanguage",fallback);}
}
