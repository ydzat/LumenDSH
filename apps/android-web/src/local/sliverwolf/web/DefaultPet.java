package local.sliverwolf.web;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;

/** Bundled v1 artwork shares the renderer's nine activity rows. */
final class DefaultPet {
    static PetSprite load(AssetManager assets) throws IOException {
        Bitmap bitmap;
        try(InputStream input=assets.open("default-pet/spritesheet.webp")) {
            bitmap=BitmapFactory.decodeStream(input);
        }
        if(bitmap==null) throw new IOException("Cannot decode bundled Silver Wolf");
        if(bitmap.getWidth()!=1536||bitmap.getHeight()!=1872) {
            bitmap.recycle(); throw new IOException("Unexpected bundled Silver Wolf atlas dimensions");
        }
        return new PetSprite(bitmap);
    }
}
