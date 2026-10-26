package local.sliverwolf.web;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Inert import, validated immutable versions, then an explicit atomic active pointer. */
final class PetStore {
    static final int MAX_ARCHIVE=8*1024*1024, MAX_EXPANDED=16*1024*1024;
    final File root;
    private final AtomicFile active;
    static final class Pack {
        final String key, name;
        final PetSprite sprite;
        Pack(String key,String name,Bitmap bitmap) { this.key=key; this.name=name; sprite=new PetSprite(bitmap); }
    }
    PetStore(File root) throws IOException {
        this.root=root;
        if(!root.isDirectory()&&!root.mkdirs()) throw new IOException("Cannot create appearance storage");
        active=new AtomicFile(new File(root,"active.json"));
    }
    synchronized Pack importZip(InputStream input) throws Exception {
        File archive=File.createTempFile("import-",".zip",root);
        File stage=null;
        try {
            try(FileOutputStream out=new FileOutputStream(archive)) { copy(input,out,MAX_ARCHIVE); }
            String key=hash(archive);
            Map<String,ZipEntry> entries=new LinkedHashMap<>();
            inspectDirectory(archive);
            try(ZipFile zip=new ZipFile(archive)) {
                String manifestPath=null;
                Set<String> names=new HashSet<>();
                Enumeration<? extends ZipEntry> iterator=zip.entries();
                int count=0; long expanded=0;
                while(iterator.hasMoreElements()) {
                    ZipEntry e=iterator.nextElement(); String name=e.getName(); safePath(name,e.isDirectory());
                    if(++count>32||!names.add(name.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate path or too many entries");
                    if(e.getSize()<0||e.getSize()>MAX_EXPANDED||(expanded+=e.getSize())>MAX_EXPANDED)
                        throw new IOException("Expanded archive exceeds 16 MiB");
                    if(e.isDirectory()) continue;
                    entries.put(name,e);
                    if(name.equals("pet.json")||name.endsWith("/pet.json")) {
                        if(manifestPath!=null) throw new IOException("Multiple pet.json files");
                        manifestPath=name;
                    }
                }
                if(manifestPath==null) throw new IOException("Missing pet.json");
                byte[] raw=readEntry(zip,entries.get(manifestPath),65536);
                JSONObject pet=new JSONObject(new String(raw,StandardCharsets.UTF_8));
                if(!(pet.opt("spriteVersionNumber") instanceof Number)||pet.getDouble("spriteVersionNumber")!=2)
                    throw new IOException("Only spriteVersionNumber 2 is supported");
                String id=string(pet,"id",128), sheetPath=string(pet,"spritesheetPath",240);
                safePath(sheetPath,false);
                String name=pet.has("displayName")?string(pet,"displayName",128):id;
                String parent=manifestPath.substring(0,manifestPath.lastIndexOf('/')+1);
                String sheet=parent+sheetPath;
                if(!entries.containsKey(sheet)||!(sheet.endsWith(".png")||sheet.endsWith(".webp")))
                    throw new IOException("Missing PNG/WebP sprite sheet");
                for(String path:entries.keySet()) {
                    String base=path.substring(path.lastIndexOf('/')+1).toLowerCase(Locale.ROOT);
                    if(!path.equals(manifestPath)&&!path.equals(sheet)&&
                       !base.matches("(license|licence|readme|notice)(\\.txt|\\.md)?"))
                        throw new IOException("Unsupported archive content: "+path);
                }
                File destination=new File(root,key);
                if(destination.isDirectory()) return load(key);
                File[] installed=root.listFiles(f->f.isDirectory()&&f.getName().matches("[0-9a-f]{64}"));
                if(installed==null||installed.length>=8) throw new IOException("Appearance storage has reached eight versions");
                stage=new File(root,"stage-"+UUID.randomUUID());
                if(!stage.mkdir()) throw new IOException("Cannot create staging directory");
                JSONArray files=new JSONArray();
                save(stage,"pet.json",raw,files);
                byte[] atlas=readEntry(zip,entries.get(sheet),MAX_EXPANDED);
                String localSheet=sheet.endsWith(".webp")?"atlas.webp":"atlas.png";
                save(stage,localSheet,atlas,files);
                int docs=0;
                for(String path:entries.keySet()) if(!path.equals(manifestPath)&&!path.equals(sheet))
                    save(stage,"notice-"+(docs++)+".txt",readEntry(zip,entries.get(path),65536),files);
                JSONObject normalized=new JSONObject().put("schemaVersion",1).put("kind","appearance")
                    .put("id",id).put("version",key).put("displayName",name)
                    .put("author",pet.has("author")?string(pet,"author",256):"unspecified")
                    .put("license",pet.has("license")?string(pet,"license",256):"unspecified; see retained notices")
                    .put("source","codex-pet-v2 / user-selected archive").put("archiveSha256",key)
                    .put("requires",new JSONObject().put("renderer","codex-pet-v2"))
                    .put("entry",localSheet).put("files",files);
                write(new File(stage,"appearance.json"),normalized.toString(2).getBytes(StandardCharsets.UTF_8));
                Bitmap bitmap=decode(new File(stage,localSheet));
                if(!stage.renameTo(destination)) { bitmap.recycle(); throw new IOException("Cannot finalize appearance version"); }
                stage=null;
                return new Pack(key,name,bitmap);
            }
        } finally {
            Files.deleteIfExists(archive.toPath());
            if(stage!=null) { File[] files=stage.listFiles(); if(files!=null) for(File f:files) Files.deleteIfExists(f.toPath()); Files.deleteIfExists(stage.toPath()); }
        }
    }
    synchronized Pack restore() throws Exception {
        if(!active.getBaseFile().exists()&&!new File(root,"active.json.bak").exists()) return null;
        JSONObject value=new JSONObject(new String(active.readFully(),StandardCharsets.UTF_8));
        String key=value.getString("key"); return key.isEmpty()?null:load(key);
    }
    synchronized LinkedHashMap<String,String> appearances() throws Exception {
        LinkedHashMap<String,String> result=new LinkedHashMap<>();result.put("","银狼（默认）");
        File[] entries=root.listFiles();if(entries==null)throw new IOException("Cannot list appearances");
        for(File entry:entries)if(entry.isDirectory()&&entry.getName().matches("[0-9a-f]{64}"))
            result.put(entry.getName(),verify(entry.getName()).getString("displayName"));
        return result;
    }
    synchronized String activeProfile() throws Exception {
        if(!active.getBaseFile().exists()&&!new File(root,"active.json.bak").exists()) return "";
        return new JSONObject(new String(active.readFully(),StandardCharsets.UTF_8)).optString("profile","");
    }
    synchronized void activate(Pack pack) throws Exception { activate(pack, "",null); }
    synchronized void activate(Pack pack,String profile,AssistantProfile candidate) throws Exception {
        if(!profile.isEmpty()&&!profile.matches("[0-9a-f]{64}")) throw new IOException("Invalid profile reference");
        if(!profile.isEmpty()&&(candidate==null||!candidate.appearance.equals(pack==null?"":pack.key)||
                !AssistantProfile.digest(candidate.json().toString()).equals(profile))) throw new IOException("Profile components do not match activation");
        // Revalidate persisted payloads immediately before the pointer changes.
        if(pack!=null) verify(pack.key);
        FileOutputStream stream=null;
        try {
            stream=active.startWrite();
            stream.write(new JSONObject().put("key",pack==null?"":pack.key).put("profile",profile).toString().getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync(); active.finishWrite(stream);
        } catch(Exception e) { if(stream!=null) active.failWrite(stream); throw e; }
    }
    synchronized Pack load(String key) throws Exception {
        JSONObject record=verify(key);
        return new Pack(key,record.getString("displayName"),decode(new File(new File(root,key),record.getString("entry"))));
    }
    synchronized void remove(String key,AssistantProfile.Store profiles) throws Exception {
        if(!key.matches("[0-9a-f]{64}")) throw new IOException("Only imported appearances can be removed");
        if(active.getBaseFile().exists()||new File(root,"active.json.bak").exists()) {
            JSONObject selected=new JSONObject(new String(active.readFully(),StandardCharsets.UTF_8));
            if(key.equals(selected.getString("key")))throw new IOException("Switch appearances before deleting the active version");
        }
        for(AssistantProfile profile:profiles.list().values())if(key.equals(profile.appearance))
            throw new IOException("A saved profile uses this appearance; remove that profile first");
        File directory=new File(root,key);
        if(Files.isSymbolicLink(directory.toPath())||!directory.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()))
            throw new IOException("Invalid appearance storage path");
        File[] files=directory.listFiles();if(files==null)throw new IOException("Appearance directory unavailable");
        for(File file:files)if(Files.isSymbolicLink(file.toPath())||!file.isFile())throw new IOException("Unexpected appearance entry");
        for(File file:files)Files.delete(file.toPath());
        Files.delete(directory.toPath());
    }
    private JSONObject verify(String key) throws Exception {
        if(!key.matches("[0-9a-f]{64}")) throw new IOException("Invalid appearance version");
        File directory=new File(root,key);
        JSONObject record=new JSONObject(new String(read(new File(directory,"appearance.json"),65536),StandardCharsets.UTF_8));
        if(record.getInt("schemaVersion")!=1||!"appearance".equals(record.getString("kind"))||!key.equals(record.getString("version")))
            throw new IOException("Invalid normalized manifest");
        String sheet=record.getString("entry");
        if(!sheet.equals("atlas.png")&&!sheet.equals("atlas.webp")) throw new IOException("Invalid atlas entry");
        JSONArray files=record.getJSONArray("files"); Set<String> names=new HashSet<>();
        if(files.length()>32) throw new IOException("Too many manifest files");
        for(int i=0;i<files.length();i++) {
            JSONObject item=files.getJSONObject(i); String path=item.getString("path"); safePath(path,false);
            if(path.contains("/")||!names.add(path)) throw new IOException("Invalid normalized file path");
            File f=new File(directory,path);
            if(Files.isSymbolicLink(f.toPath())||!f.isFile()||f.length()!=item.getLong("size")||f.length()>MAX_EXPANDED||!hash(f).equals(item.getString("sha256")))
                throw new IOException("Appearance integrity check failed");
        }
        if(!names.contains(sheet)||!names.contains("pet.json")) throw new IOException("Missing declared payload");
        return record;
    }
    private static Bitmap decode(File file) throws IOException {
        BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getPath(),options);
        if(options.outWidth!=1536||options.outHeight!=2288||
           !("image/png".equals(options.outMimeType)||"image/webp".equals(options.outMimeType)))
            throw new IOException("Atlas must be a 1536 x 2288 PNG/WebP");
        options.inJustDecodeBounds=false; options.inScaled=false; options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap bitmap=BitmapFactory.decodeFile(file.getPath(),options);
        if(bitmap==null) throw new IOException("Cannot decode sprite sheet");
        return bitmap;
    }
    private static String string(JSONObject value,String key,int limit) throws Exception {
        Object raw=value.get(key);
        if(!(raw instanceof String)||((String)raw).trim().isEmpty()||((String)raw).length()>limit)
            throw new IOException("Invalid "+key);
        return (String)raw;
    }
    static void safePath(String name,boolean directory) throws IOException {
        if(name.isEmpty()||name.length()>240||name.startsWith("/")||name.contains("\\")||name.contains(":")) throw new IOException("Unsafe archive path");
        String path=directory&&name.endsWith("/")?name.substring(0,name.length()-1):name;
        for(String part:path.split("/",-1)) if(part.isEmpty()||part.equals(".")||part.equals("..")) throw new IOException("Unsafe archive path");
        for(int i=0;i<name.length();i++) if(Character.isISOControl(name.charAt(i))) throw new IOException("Unsafe archive path");
    }
    private static byte[] readEntry(ZipFile zip,ZipEntry entry,int limit) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=zip.getInputStream(entry)) { copy(in,out,limit); }
        byte[] bytes=out.toByteArray(); CRC32 crc=new CRC32(); crc.update(bytes);
        if(bytes.length!=entry.getSize()||crc.getValue()!=entry.getCrc()) throw new IOException("ZIP size or CRC mismatch");
        return bytes;
    }
    private static byte[] read(File file,int limit) throws IOException {
        try(InputStream in=new FileInputStream(file)) { ByteArrayOutputStream out=new ByteArrayOutputStream(); copy(in,out,limit); return out.toByteArray(); }
    }
    private static void copy(InputStream in,OutputStream out,int limit) throws IOException {
        byte[] buffer=new byte[8192]; int total=0,n;
        while((n=in.read(buffer))!=-1) { total+=n; if(total>limit) throw new IOException("File exceeds size limit"); out.write(buffer,0,n); }
    }
    private static void write(File file,byte[] bytes) throws IOException { try(FileOutputStream out=new FileOutputStream(file)) { out.write(bytes); out.getFD().sync(); } }
    private static void save(File directory,String name,byte[] bytes,JSONArray files) throws Exception {
        File file=new File(directory,name); write(file,bytes);
        files.put(new JSONObject().put("path",name).put("size",bytes.length).put("sha256",hash(file)));
    }
    static String hash(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)) { byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) digest.update(b,0,n); }
        StringBuilder result=new StringBuilder(); for(byte b:digest.digest()) result.append(String.format(Locale.ROOT,"%02x",b&255)); return result.toString();
    }
    /** ZipFile does not expose Unix modes on API 29; inspect only the bounded central directory. */
    private static void inspectDirectory(File file) throws IOException {
        byte[] bytes=read(file,MAX_ARCHIVE); int end=-1;
        for(int i=bytes.length-22;i>=Math.max(0,bytes.length-65557);i--)
            if(u32(bytes,i)==0x06054b50L&&i+22+u16(bytes,i+20)==bytes.length) { end=i; break; }
        if(end<0||u16(bytes,end+4)!=0||u16(bytes,end+6)!=0) throw new IOException("Unsupported ZIP directory");
        int count=u16(bytes,end+10); long offset=u32(bytes,end+16),size=u32(bytes,end+12);
        if(count<1||count>32||u16(bytes,end+8)!=count||offset+size!=end) throw new IOException("ZIP64/multipart/oversized ZIP is unsupported");
        int pos=(int)offset;
        for(int i=0;i<count;i++) {
            if(pos<0||pos+46>end||u32(bytes,pos)!=0x02014b50L) throw new IOException("Invalid central directory");
            int mode=(int)(u32(bytes,pos+38)>>>16)&0170000;
            if(mode!=0&&mode!=0100000&&mode!=0040000) throw new IOException("Symbolic links and special files are unsupported");
            if((u16(bytes,pos+8)&1)!=0||u16(bytes,pos+34)!=0) throw new IOException("Encrypted/multipart ZIP is unsupported");
            pos+=46+u16(bytes,pos+28)+u16(bytes,pos+30)+u16(bytes,pos+32);
        }
        if(pos!=end) throw new IOException("Invalid central directory length");
    }
    private static int u16(byte[] b,int p) { return (b[p]&255)|((b[p+1]&255)<<8); }
    private static long u32(byte[] b,int p) { return (long)u16(b,p)|((long)u16(b,p+2)<<16); }
}
