package local.sliverwolf.assistant;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Private encrypted embeddings only; no raw voice recording or export API. */
final class OwnerVoiceStore {
    static final String MODEL="f682b514c05d947ee3fa91cd6ec6c5c7543479a128373fa29b1faedccd21fd11";
    private final AtomicFile file;
    private final String alias;
    private final String binding;
    private int minimum=3,maximum=3;
    OwnerVoiceStore(Context context){this(new File(context.getFilesDir(),"owner-voice.bin"),"sliver-wolf-owner-voice-v1");}
    OwnerVoiceStore(File path,String alias){this(path,alias,MODEL);}
    private OwnerVoiceStore(File path,String alias,String binding){file=new AtomicFile(path);this.alias=alias;this.binding=binding;}
    static OwnerVoiceStore wake(Context context,String name){return new OwnerVoiceStore(new File(context.getFilesDir(),"owner-wake.bin"),"sliver-wolf-owner-wake-v1",MODEL+"\n"+name);}
    static OwnerVoiceStore learned(Context context,String name)throws Exception {
        String anchor=PetStore.hash(new File(context.getFilesDir(),"owner-voice.bin"));
        OwnerVoiceStore store=new OwnerVoiceStore(new File(context.getFilesDir(),"owner-learned.bin"),"sliver-wolf-owner-learned-v1",MODEL+"\n"+name+"\n"+anchor);
        store.minimum=1;store.maximum=9;return store;
    }
    static final class BindingMismatch extends IOException {BindingMismatch(){super("Wake phrase changed; calibration is not applicable");}}
    boolean exists(){return file.getBaseFile().exists()||new File(file.getBaseFile()+".bak").exists();}
    private SecretKey key(boolean create)throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(alias))return (SecretKey)store.getKey(alias,null);
        if(!create)throw new IOException("Owner voice key is unavailable; enroll again");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    void save(float[][] vectors)throws Exception {
        if(vectors.length<minimum||vectors.length>maximum)throw new IOException("Invalid sample count");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)) {
            out.writeInt(1);out.writeUTF(binding);out.writeInt(vectors.length);
            for(float[] vector:vectors){validate(vector);out.writeInt(vector.length);for(float value:vector)out.writeFloat(value);}
        }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));
        byte[] encrypted=cipher.doFinal(bytes.toByteArray()),iv=cipher.getIV();
        FileOutputStream stream=null;
        try {
            stream=file.startWrite();DataOutputStream out=new DataOutputStream(stream);
            out.writeInt(1);out.writeInt(iv.length);out.write(iv);out.write(encrypted);out.flush();file.finishWrite(stream);
        }catch(Exception error){if(stream!=null)file.failWrite(stream);throw error;}
    }
    float[][] load()throws Exception {
        byte[] stored=file.readFully();if(stored.length>32768)throw new IOException("Invalid owner record size");
        DataInputStream input=new DataInputStream(new ByteArrayInputStream(stored));
        if(input.readInt()!=1||input.readInt()!=12)throw new IOException("Invalid owner envelope");
        byte[] iv=new byte[12];input.readFully(iv);byte[] encrypted=new byte[input.available()];input.readFully(encrypted);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,iv));
        DataInputStream plain=new DataInputStream(new ByteArrayInputStream(cipher.doFinal(encrypted)));
        if(plain.readInt()!=1)throw new IOException("Owner record version mismatch");
        if(!binding.equals(plain.readUTF()))throw new BindingMismatch();
        int samples=plain.readInt();if(samples<minimum||samples>maximum)throw new IOException("Owner enrollment is incomplete");
        float[][] vectors=new float[samples][];
        for(int i=0;i<samples;i++) {
            int count=plain.readInt();if(count<64||count>1024)throw new IOException("Invalid embedding dimension");
            vectors[i]=new float[count];for(int j=0;j<count;j++)vectors[i][j]=plain.readFloat();validate(vectors[i]);
        }
        if(plain.available()!=0)throw new IOException("Unexpected owner record content");return vectors;
    }
    private static void validate(float[] vector)throws IOException {
        if(vector.length<64||vector.length>1024)throw new IOException("Invalid embedding dimension");
        double norm=0;for(float value:vector){if(!Float.isFinite(value))throw new IOException("Invalid embedding value");norm+=value*value;}
        if(norm<1e-8)throw new IOException("Empty embedding");
    }
    void remove()throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(alias);file.delete();
    }
}
