package local.sliverwolf.assistant;

import java.io.*;
import java.nio.file.Files;

/** Runs production importer, BitmapFactory and AtomicFile on the actual Android runtime. */
public final class PetImportChecks {
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static PetStore.Pack imported(PetStore store,File fixtures,String file) throws Exception {
        try(InputStream input=new FileInputStream(new File(fixtures,file))) { return store.importZip(input); }
    }
    public static void main(String[] args) throws Exception {
        require(PetSprite.presentationFrameAt(0,559)==0&&PetSprite.presentationFrameAt(0,560)==1,"Half-speed frame boundary");
        require(PetSprite.presentationFrameAt(0,2200)==0&&PetSprite.presentationFrameAt(0,6199)==0&&PetSprite.presentationFrameAt(0,6760)==1,"Idle rests then restarts");
        File fixtures=new File(args[0]);
        File root=Files.createTempDirectory(new File(args[1]).toPath(),"pet-check-").toFile();
        PetStore store=new PetStore(root);
        PetStore.Pack original=imported(store,fixtures,"valid-v2.zip");
        require(store.restore()==null,"Import must not activate");
        require(original.sprite.bitmap.getWidth()==1536&&original.sprite.bitmap.getHeight()==2288,"Actual bitmap dimensions");
        android.graphics.Bitmap rendered=android.graphics.Bitmap.createBitmap(192,208,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(rendered);
        for(String state:new String[]{"idle","listening","thinking","working","speaking","waiting","error"}) {
            rendered.eraseColor(0);
            require(original.sprite.draw(canvas,0,0,192,208,state,1000,false),"Renderer supplies "+state);
            android.graphics.Bitmap expected=android.graphics.Bitmap.createBitmap(original.sprite.bitmap,0,PetSprite.semanticRow(state)*208,192,208);
            require(rendered.sameAs(expected),"Actual rendered frame matches atlas row: "+state); expected.recycle();
        }
        for(long now:new long[]{1000,1100,1200,1300,1400,1500,1600}) {rendered.eraseColor(0); original.sprite.draw(canvas,0,0,192,208,"idle",now,true);}
        android.graphics.Bitmap second=android.graphics.Bitmap.createBitmap(original.sprite.bitmap,192,0,192,208);
        require(rendered.sameAs(second),"Animation advances to actual second frame");
        original.sprite.draw(canvas,0,0,192,208,"idle",5000,false);
        rendered.eraseColor(0);original.sprite.draw(canvas,0,0,192,208,"idle",9000,true);
        require(rendered.sameAs(second),"Focus pause resumes without catching up");second.recycle();rendered.recycle();
        store.activate(original);
        for(String file:new String[]{"traversal.zip","absolute.zip","duplicate.zip","executable.zip","wrong-version.zip",
                "sheet-escape.zip","invalid-image.zip","wrong-dimensions.zip","expansion.zip","symlink.zip","truncated.zip"}) {
            boolean rejected=false;
            try { imported(store,fixtures,file); } catch(Exception expected) { rejected=true; }
            require(rejected,"Must reject "+file);
            PetStore.Pack retained=store.restore(); require(retained.key.equals(original.key),"Previous pointer retained: "+file);
            retained.sprite.bitmap.recycle(); System.out.println("REJECTED_AND_RETAINED "+file);
        }
        File huge=new File(root,"too-large.zip");
        try(RandomAccessFile f=new RandomAccessFile(huge,"rw")) { f.setLength(PetStore.MAX_ARCHIVE+1L); }
        boolean rejected=false;
        try(InputStream in=new FileInputStream(huge)) { store.importZip(in); } catch(IOException expected) { rejected=true; }
        require(rejected,"Compressed input limit");
        require(!new File(root,"escape.txt").exists()&&!new File(root.getParentFile(),"escape.txt").exists(),"No traversal output");
        PetStore.Pack nested=imported(store,fixtures,"nested-v2.zip"); nested.sprite.bitmap.recycle();
        File webpZip=new File(root,"webp.zip");
        try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new FileOutputStream(webpZip))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("pet.json"));
            zip.write("{\"id\":\"webp-check\",\"spriteVersionNumber\":2,\"spritesheetPath\":\"sprite.webp\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("sprite.webp"));
            require(original.sprite.bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP,100,zip),"Android WebP encoding");zip.closeEntry();
        }
        try(InputStream input=new FileInputStream(webpZip)) {
            PetStore.Pack webp=store.importZip(input); require(webp.sprite.bitmap.getWidth()==1536,"WebP decode/import");webp.sprite.bitmap.recycle();
        }
        PetStore.Pack fallback=imported(store,fixtures,"missing-wave.zip");
        require(fallback.sprite.resolveRow("speaking")==0,"Blank speaking row falls back to idle"); fallback.sprite.bitmap.recycle();
        require(PetSprite.semanticRow("listening")==6&&PetSprite.semanticRow("working")==7&&PetSprite.semanticRow("error")==5,"Semantic mapping");
        require(PetSprite.frameAt(0,279)==0&&PetSprite.frameAt(0,280)==1&&PetSprite.frameAt(0,1100)==0,"Pinned frame boundaries and loop");
        File sheet=new File(new File(root,original.key),"atlas.png");
        try(RandomAccessFile f=new RandomAccessFile(sheet,"rw")) { f.seek(50); int value=f.read(); f.seek(50); f.write(value^255); }
        rejected=false; try {store.activate(original);} catch(Exception expected) {rejected=true;}
        require(rejected,"Recheck integrity before activation");
        // Recover a corrupt imported selection by an explicit built-in selection.
        store.activate(null); require(store.restore()==null,"Built-in selection persists");
        original.sprite.bitmap.recycle();
        System.out.println("PET_IMPORT_CHECKS_PASS (Android BitmapFactory, actual ZIP files, atomic pointer)");
    }
}
