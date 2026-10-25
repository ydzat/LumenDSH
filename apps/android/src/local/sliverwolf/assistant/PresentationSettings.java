package local.sliverwolf.assistant;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded subset of upstream loadPetConfig; appearance and voice ownership stay separate. */
final class PresentationSettings {
    final String name,tag;
    final float size,animationSpeed;
    final int[] phraseWeights;
    final int[] contentWeights={4,1,2,2,1};
    final int textColor,backgroundColor,holdSeconds;
    final String[] phrases;
    PresentationSettings(JSONObject json) throws Exception {
        java.util.Set<String> allowed=new java.util.HashSet<>(java.util.Arrays.asList("schemaVersion","name","tag","size","textColor","backgroundColor","holdSeconds","phrases","animationSpeed","phraseWeights","contentWeights"));
        java.util.Iterator<String> keys=json.keys();while(keys.hasNext()){String key=keys.next();if(!allowed.contains(key))throw new IllegalArgumentException("Unknown presentation field: "+key);}
        if(json.getInt("schemaVersion")!=1)throw new IllegalArgumentException("Unsupported presentation schema");
        name=bounded(json.getString("name"),24);tag=bounded(json.getString("tag"),32);
        size=(float)json.getDouble("size");
        if(!Float.isFinite(size)||size<.7f||size>1.2f)throw new IllegalArgumentException("Size must be 0.7..1.2");
        animationSpeed=(float)json.optDouble("animationSpeed",1);
        if(!Float.isFinite(animationSpeed)||animationSpeed<.5f||animationSpeed>1.5f)throw new IllegalArgumentException("Animation speed must be 0.5..1.5");
        textColor=android.graphics.Color.parseColor(json.getString("textColor"));
        backgroundColor=android.graphics.Color.parseColor(json.getString("backgroundColor"));
        holdSeconds=json.getInt("holdSeconds");
        if(json.getDouble("holdSeconds")!=holdSeconds||holdSeconds<5||holdSeconds>60)throw new IllegalArgumentException("Hold must be 5..60 seconds");
        JSONArray lines=json.getJSONArray("phrases");
        if(lines.length()>20)throw new IllegalArgumentException("At most 20 phrases");
        phrases=new String[lines.length()];for(int i=0;i<phrases.length;i++)phrases[i]=bounded(lines.getString(i),160);
        phraseWeights=new int[phrases.length];java.util.Arrays.fill(phraseWeights,1);
        if(json.has("phraseWeights")) {
            JSONArray weights=json.getJSONArray("phraseWeights");if(weights.length()!=phrases.length)throw new IllegalArgumentException("Phrase weights must match phrase count");
            for(int i=0;i<phraseWeights.length;i++){int weight=weights.getInt(i);if(weights.getDouble(i)!=weight||weight<1||weight>100)throw new IllegalArgumentException("Phrase weights must be integers 1..100");phraseWeights[i]=weight;}
        }
        if(json.has("contentWeights")) {
            JSONObject weights=json.getJSONObject("contentWeights");
            java.util.Set<String> known=new java.util.HashSet<>();
            for(AttentionQueue.Source source:AttentionQueue.Source.values()) {
                known.add(source.key);if(!weights.has(source.key))continue;
                int weight=weights.getInt(source.key);
                if(weights.getDouble(source.key)!=weight||weight<1||weight>100)throw new IllegalArgumentException("Content weights must be integers 1..100");
                contentWeights[source.ordinal()]=weight;
            }
            java.util.Iterator<String> fields=weights.keys();while(fields.hasNext())if(!known.contains(fields.next()))throw new IllegalArgumentException("Unknown content source");
        }
    }
    String phrase(java.util.Random random) {
        int total=0;for(int weight:phraseWeights)total+=weight;if(total==0)return "";
        int value=random.nextInt(total);for(int i=0;i<phrases.length;i++){value-=phraseWeights[i];if(value<0)return phrases[i];}throw new AssertionError();
    }
    private static String bounded(String value,int max){
        if(value.trim().isEmpty()||value.length()>max)throw new IllegalArgumentException("Text length must be 1.."+max);
        return value;
    }
    static String defaults(){return "{\"schemaVersion\":1,\"name\":\"银狼\",\"tag\":\"LOCAL\",\"size\":1,\"textColor\":\"#E0E5F0\",\"backgroundColor\":\"#202C3C\",\"holdSeconds\":12,\"phrases\":[]}";}
    static String stored(Context c){return c.getSharedPreferences("presentation",0).getString("json",defaults());}
    static PresentationSettings load(Context c)throws Exception{return parse(stored(c));}
    static PresentationSettings parse(String text)throws Exception {
        if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>8192)throw new IllegalArgumentException("Presentation settings exceed 8 KiB");
        return new PresentationSettings(new JSONObject(text));
    }
    static String digest(String text)throws Exception {
        byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte value:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return out.toString();
    }
    static String archive(java.io.File root,String text)throws Exception {
        parse(text);if(!root.isDirectory()&&!root.mkdirs())throw new java.io.IOException("Cannot create presentation history");
        String id=digest(text);java.io.File file=new java.io.File(root,id+".json");
        if(file.isFile()){if(!readVersion(root,id).equals(text))throw new java.io.IOException("Presentation version collision");return id;}
        android.util.AtomicFile atomic=new android.util.AtomicFile(file);java.io.FileOutputStream out=null;
        try{out=atomic.startWrite();out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));atomic.finishWrite(out);}
        catch(Exception error){if(out!=null)atomic.failWrite(out);throw error;}return id;
    }
    static String readVersion(java.io.File root,String id)throws Exception {
        if(!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid presentation version");
        java.io.File file=new java.io.File(root,id+".json");if(file.length()>8192)throw new java.io.IOException("Presentation version too large");
        String text=new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8);
        parse(text);if(!digest(text).equals(id))throw new java.io.IOException("Presentation integrity mismatch");return text;
    }
    static java.io.File history(Context c){return new java.io.File(c.getFilesDir(),"presentation-versions");}
    static PresentationSettings save(Context c,String text)throws Exception {
        PresentationSettings result=parse(text);
        archive(history(c),stored(c));String version=archive(history(c),text);
        if(!c.getSharedPreferences("presentation",0).edit().putString("json",text).putString("version",version).commit())
            throw new java.io.IOException("Cannot persist presentation settings");return result;
    }
}
