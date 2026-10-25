package local.sliverwolf.assistant;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/** Fixed v2 atlas mapping adapted from pinned dsh-pet PetContract.ts (MIT). */
final class PetSprite {
    // Copyright (c) 2026 sereinmono and dsh-pet contributors. See THIRD_PARTY_NOTICES.txt.
    static final int[][] DURATIONS = {
        {280,110,110,140,140,320}, {120,120,120,120,120,120,120,220},
        {120,120,120,120,120,120,120,220}, {140,140,140,280},
        {140,140,140,140,280}, {140,140,140,140,140,140,140,240},
        {150,150,150,150,150,260}, {120,120,120,120,120,220},
        {150,150,150,150,150,280}
    };
    final Bitmap bitmap;
    final boolean[] available = new boolean[9];
    private final Paint paint = new Paint();
    private final Rect source = new Rect();
    private final RectF target = new RectF();
    private int previousRow = -2;
    private long elapsed, lastTime;
    PetSprite(Bitmap bitmap) {
        this.bitmap=bitmap;
        int[] pixels=new int[192*208];
        for(int row=0;row<9;row++) for(int frame=0;frame<DURATIONS[row].length&&!available[row];frame++) {
            bitmap.getPixels(pixels,0,192,frame*192,row*208,192,208);
            for(int pixel:pixels) if((pixel>>>24)!=0) { available[row]=true; break; }
        }
    }
    static int semanticRow(String state) {
        switch(state) {
            case "listening": case "waiting": return 6;
            case "thinking": return 8;
            case "working": return 7;
            case "speaking": return 3;
            case "error": return 5;
            default: return 0;
        }
    }
    int resolveRow(String state) { int row=semanticRow(state); return available[row]?row:available[0]?0:-1; }
    static int frameAt(int row,long elapsed) {
        int total=0; for(int t:DURATIONS[row]) total+=t;
        long time=Math.max(0,elapsed)%total;
        for(int i=0;i<DURATIONS[row].length;i++) { if(time<DURATIONS[row][i]) return i; time-=DURATIONS[row][i]; }
        return 0;
    }
    // Keep source timing intact; presentation runs at half speed with an idle rest.
    static int presentationFrameAt(int row,long elapsed) {
        long time=Math.max(0,elapsed);
        if(row==0) {
            long cycle=0; for(int duration:DURATIONS[0]) cycle+=duration*2L;
            time%=cycle+4000;
            if(time>=cycle) return 0;
        }
        return frameAt(row,time/2);
    }
    float playbackSpeed=1;
    void pause() { lastTime=0; }
    boolean draw(Canvas canvas,float left,float top,float width,float height,String state,long now,boolean animate) {
        int row=resolveRow(state); if(row<0) return false;
        if(row!=previousRow) { elapsed=0; previousRow=row; lastTime=0; }
        if(animate&&lastTime!=0) elapsed+=Math.round(Math.min(100,Math.max(0,now-lastTime))*playbackSpeed);
        lastTime=animate?now:0;
        int frame=presentationFrameAt(row,elapsed);
        source.set(frame*192,row*208,(frame+1)*192,(row+1)*208);
        target.set(left,top,left+width,top+height);
        canvas.drawBitmap(bitmap,source,target,paint); return true;
    }
}
