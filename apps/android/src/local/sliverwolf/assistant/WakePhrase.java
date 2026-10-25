package local.sliverwolf.assistant;

import android.icu.text.Transliterator;
import java.text.Normalizer;
import java.util.Set;

/** Derives model tokens from the same display name used by the habitat. */
final class WakePhrase {
    final String display, encoded;
    private WakePhrase(String display,String encoded){this.display=display;this.encoded=encoded;}
    static WakePhrase compile(String name,Set<String> vocabulary) {
        String value=name.trim();
        if(value.isEmpty()||value.length()>24)throw new IllegalArgumentException("Agent name must contain 1..24 characters");
        // The initial model is Chinese. Reject unsupported names instead of keeping an old keyword.
        for(int i=0;i<value.length();) {
            int cp=value.codePointAt(i);i+=Character.charCount(cp);
            if(Character.UnicodeScript.of(cp)!=Character.UnicodeScript.HAN)
                throw new IllegalArgumentException("当前唤醒模型要求中文角色名："+value);
        }
        String pinyin=Normalizer.normalize(Transliterator.getInstance("Han-Latin").transliterate(value),Normalizer.Form.NFC);
        StringBuilder tokens=new StringBuilder("h āi");
        for(String syllable:pinyin.trim().split("\\s+")) {
            String initial="";
            for(String candidate:new String[]{"zh","ch","sh","b","p","m","f","d","t","n","l","g","k","h","j","q","x","r","z","c","s","y","w"})
                if(syllable.startsWith(candidate)){initial=candidate;break;}
            String ending=syllable.substring(initial.length());
            if((!initial.isEmpty()&&!vocabulary.contains(initial))||!vocabulary.contains(ending))
                throw new IllegalArgumentException("唤醒模型不支持该名称的读音："+syllable);
            if(!initial.isEmpty())tokens.append(' ').append(initial);
            tokens.append(' ').append(ending);
        }
        for(String token:tokens.toString().split(" "))if(!vocabulary.contains(token))
            throw new IllegalArgumentException("Wake vocabulary is missing token "+token);
        String suffix=tokens.substring("h āi".length());
        StringBuilder variants=new StringBuilder();
        for(String greeting:new String[]{"āi","ái","ǎi","ài"}) {
            if(!vocabulary.contains(greeting))throw new IllegalArgumentException("Greeting token unavailable");
            if(variants.length()>0)variants.append('\n');
            variants.append("h ").append(greeting).append(suffix).append(" :1.5 #0.25 @wake");
        }
        return new WakePhrase("嗨，"+value,variants.toString());
    }
}
