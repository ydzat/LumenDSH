package local.sliverwolf.assistant;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Provider credentials stay device-local; immutable voice selections can be referenced by profiles. */
final class VoiceProviders {
    static final String PREFIX="lumendsh.voice.";
    private final Context context;
    private final SharedPreferences prefs;
    VoiceProviders(Context context){this.context=context;this.prefs=context.getSharedPreferences("voice-providers",0);}
    static boolean handles(String engine){return CharacterSpeech.ENGINE.equals(engine)||engine.startsWith(PREFIX);}
    static final class Provider {
        final String id,name,url,key; final boolean cancel;
        Provider(String id,String name,String url,String key,boolean cancel){this.id=id;this.name=name;this.url=url;this.key=key;this.cancel=cancel;}
    }
    static final class Selection {
        final Provider provider; final String model,voice; final boolean legacy;
        Selection(Provider p,String m,String v,boolean old){provider=p;model=m;voice=v;legacy=old;}
    }
    Provider provider(String id)throws Exception {
        if("local".equals(id))return new Provider(id,"手机本地","http://127.0.0.1:3086/v1",
            context.getSharedPreferences("character-voice",0).getString("token",""),true);
        JSONObject p=new JSONObject(prefs.getString("provider."+id,"{}"));
        return new Provider(id,p.getString("name"),p.getString("url"),decrypt(p.getString("key")),p.optBoolean("cancel"));
    }
    List<Provider> list()throws Exception {
        List<Provider> out=new ArrayList<>();if(!provider("local").key.isEmpty())out.add(provider("local"));
        List<String> names=new ArrayList<>(prefs.getAll().keySet());Collections.sort(names);
        for(String name:names)if(name.startsWith("provider."))out.add(provider(name.substring(9)));return out;
    }
    static String normalize(String value)throws Exception {
        URI u=new URI(value.trim());
        if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null)
            throw new IOException("远程服务地址必须是 HTTPS，不能包含密钥、查询参数或片段");
        String url=u.toASCIIString();while(url.endsWith("/"))url=url.substring(0,url.length()-1);
        return url.endsWith("/v1")?url:url+"/v1";
    }
    String save(String id,String name,String url,String key)throws Exception {
        if(name.trim().isEmpty()||name.length()>80||key.length()>4096||key.contains("\n")||key.contains("\r"))throw new IOException("服务名称或密钥无效");
        if(id==null)id=UUID.randomUUID().toString().replace("-","");
        if(!id.matches("[a-f0-9]{32}"))throw new IOException("Invalid provider ID");
        JSONObject value=new JSONObject().put("name",name.trim()).put("url",normalize(url)).put("key",encrypt(key));
        if(!prefs.edit().putString("provider."+id,value.toString()).commit())throw new IOException("Cannot save provider");return id;
    }
    void remove(String id,String active)throws Exception {
        if("local".equals(id))throw new IOException("内置本地服务不能删除");
        if(handles(active)&&!CharacterSpeech.ENGINE.equals(active)&&resolve(active).provider.id.equals(id))throw new IOException("请先切换当前声音，再删除服务");
        if(!prefs.edit().remove("provider."+id).commit())throw new IOException("Cannot remove provider");
    }
    String select(String provider,String model,String voice)throws Exception {
        if(model.trim().isEmpty()||voice.trim().isEmpty()||model.length()>200||voice.length()>200)throw new IOException("模型和音色不能为空");
        JSONObject selection=new JSONObject().put("provider",provider).put("model",model).put("voice",voice);
        String id=AssistantProfile.digest(selection.toString());
        if(!prefs.edit().putString("selection."+id,selection.toString()).commit())throw new IOException("Cannot save voice selection");return PREFIX+id;
    }
    Selection resolve(String engine)throws Exception {
        if(CharacterSpeech.ENGINE.equals(engine))return new Selection(provider("local"),"","silver-wolf",true);
        if(!engine.startsWith(PREFIX))throw new IOException("Unknown speech selection");
        JSONObject s=new JSONObject(prefs.getString("selection."+engine.substring(PREFIX.length()),"{}"));
        Provider p=provider(s.getString("provider"));
        if("local".equals(p.id)&&p.key.isEmpty())throw new IOException("本地语音服务尚未配对");
        return new Selection(p,s.getString("model"),s.getString("voice"),false);
    }
    String label(String engine){try{Selection s=resolve(engine);return s.provider.name+" · "+(s.model.isEmpty()?"银狼":s.model+" / "+s.voice);}catch(Exception e){return "语音配置不可用";}}
    boolean configured(String engine){try{resolve(engine);return true;}catch(Exception e){return false;}}
    JSONObject get(Provider p,String path)throws Exception {
        HttpURLConnection c=open(p,"GET",path);c.setReadTimeout(15000);
        try {if(c.getResponseCode()!=200)throw new IOException("目录读取失败：HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
                while((n=in.read(b))!=-1){if(out.size()+n>1024*1024)throw new IOException("目录过大");out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));}
        }finally{c.disconnect();}
    }
    JSONObject catalog(String id,String model)throws Exception {
        Provider p=provider(id);JSONObject result=get(p,model==null?"/models":"/audio/voices?model="+URLEncoder.encode(model,"UTF-8"));
        prefs.edit().putString("catalog."+id+"."+(model==null?"models":model),result.toString()).apply();
        if(model!=null&&!id.equals("local")){
            JSONObject value=new JSONObject(prefs.getString("provider."+id,"{}"));value.put("cancel",result.optBoolean("cancellation",false));prefs.edit().putString("provider."+id,value.toString()).commit();}
        return result;
    }
    static HttpURLConnection open(Provider p,String method,String path)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(p.url+path).openConnection();c.setRequestMethod(method);
        c.setConnectTimeout(10000);c.setReadTimeout(300000);c.setInstanceFollowRedirects(false);
        if(!p.key.isEmpty())c.setRequestProperty("Authorization","Bearer "+p.key);return c;
    }
    private javax.crypto.SecretKey key()throws Exception {
        String alias="lumendsh-speech-provider-v1";KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(alias))return (javax.crypto.SecretKey)store.getKey(alias,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
    }
    private String encrypt(String value)throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());byte[] bytes=c.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(bytes,Base64.NO_WRAP);
    }
    private String decrypt(String value)throws Exception {
        String[] parts=value.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
}
