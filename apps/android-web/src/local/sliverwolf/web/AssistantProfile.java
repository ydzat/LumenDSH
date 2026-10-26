package local.sliverwolf.web;

import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Immutable local composition; appearance and voice identities are versioned independently. */
final class AssistantProfile {
    static final class Voice {
        final String engine, language;
        final float rate, pitch;
        Voice(String engine,String language,float rate,float pitch) {
            if(engine==null||!engine.matches("[A-Za-z0-9_.]{1,180}")||
                    !("zh-CN".equals(language)||"en-US".equals(language))||
                    !Float.isFinite(rate)||!Float.isFinite(pitch)||rate<.5f||rate>1.5f||pitch<.75f||pitch>1.25f)
                throw new IllegalArgumentException("Invalid voice profile parameters");
            this.engine=engine;this.language=language;this.rate=rate;this.pitch=pitch;
        }
        JSONObject json() throws Exception {
            return new JSONObject().put("schemaVersion",1).put("kind","voice-profile")
                .put("engine",engine).put("language",language).put("rate",rate).put("pitch",pitch);
        }
        static Voice parse(JSONObject value) throws Exception {
            if(value.length()!=6||value.getInt("schemaVersion")!=1||!value.getString("kind").equals("voice-profile"))
                throw new IOException("Unsupported voice profile");
            return new Voice(value.getString("engine"),value.getString("language"),
                (float)value.getDouble("rate"),(float)value.getDouble("pitch"));
        }
    }
    final String name, appearance;
    final Voice voice;
    AssistantProfile(String name,String appearance,Voice voice) {
        if(name==null||name.trim().isEmpty()||name.length()>64||
                !(appearance.isEmpty()||appearance.matches("[0-9a-f]{64}"))||voice==null)
            throw new IllegalArgumentException("Invalid combined profile");
        this.name=name;this.appearance=appearance;this.voice=voice;
    }
    JSONObject json() throws Exception {
        JSONObject settings=voice.json();
        return new JSONObject().put("schemaVersion",1).put("kind","assistant-profile").put("displayName",name)
            .put("components",new JSONObject()
                .put("appearance",new JSONObject().put("version",appearance.isEmpty()?"builtin-v1":appearance))
                .put("voice",new JSONObject().put("version",digest(settings.toString())).put("config",settings)));
    }
    static AssistantProfile parse(JSONObject value) throws Exception {
        if(value.length()!=4||value.getInt("schemaVersion")!=1||!value.getString("kind").equals("assistant-profile"))
            throw new IOException("Unsupported combined profile");
        JSONObject components=value.getJSONObject("components");
        if(components.length()!=2) throw new IOException("Unsupported profile component");
        JSONObject look=components.getJSONObject("appearance");
        if(look.length()!=1) throw new IOException("Unsupported appearance reference");
        String appearance=look.getString("version");
        JSONObject v=components.getJSONObject("voice"); Voice voice=Voice.parse(v.getJSONObject("config"));
        if(v.length()!=2) throw new IOException("Unsupported voice reference");
        if(!digest(voice.json().toString()).equals(v.getString("version"))) throw new IOException("Voice version mismatch");
        return new AssistantProfile(value.getString("displayName"),appearance.equals("builtin-v1")?"":appearance,voice);
    }
    static AssistantProfile read(InputStream input) throws Exception {
        if(input==null)throw new IOException("Composition document is not readable");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int size;
        while((size=input.read(buffer))!=-1) {
            if(bytes.size()+size>8192)throw new IOException("Composition exceeds 8 KiB");
            bytes.write(buffer,0,size);
        }
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString();
        return parse(new JSONObject(text));
    }
    static String digest(String value) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder(); for(byte b:hash) out.append(String.format(Locale.ROOT,"%02x",b&255)); return out.toString();
    }
    static final class Store {
        private final File root;
        Store(File root) throws IOException { this.root=root; if(!root.isDirectory()&&!root.mkdirs()) throw new IOException("Profile storage unavailable"); }
        synchronized String save(AssistantProfile profile) throws Exception {
            String text=profile.json().toString(), key=digest(text); File file=new File(root,key+".json");
            if(file.exists()) {load(key);return key;}
            File staging=File.createTempFile("profile-",".part",root);
            try {
                try(FileOutputStream out=new FileOutputStream(staging)) {out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
                if(!staging.renameTo(file)) throw new IOException("Cannot finalize profile");
            } finally {java.nio.file.Files.deleteIfExists(staging.toPath());}
            return key;
        }
        synchronized AssistantProfile load(String key) throws Exception {
            if(!key.matches("[0-9a-f]{64}")) throw new IOException("Invalid profile version");
            File file=new File(root,key+".json");
            if(!file.isFile()||file.length()>8192||!PetStore.hash(file).equals(key)) throw new IOException("Profile integrity check failed");
            return parse(new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8)));
        }
        synchronized LinkedHashMap<String,AssistantProfile> list() throws Exception {
            LinkedHashMap<String,AssistantProfile> result=new LinkedHashMap<>(); File[] files=root.listFiles();
            if(files==null) throw new IOException("Cannot list profiles"); Arrays.sort(files,Comparator.comparing(File::getName));
            for(File file:files) if(file.getName().matches("[0-9a-f]{64}\\.json")) {
                String key=file.getName().substring(0,64); result.put(key,load(key));
            }
            return result;
        }
        synchronized void remove(String key,String active) throws Exception {
            if(key.equals(active)) throw new IOException("Switch to another profile before deleting the active profile");
            load(key);
            java.nio.file.Files.delete(new File(root,key+".json").toPath());
        }
    }
}
