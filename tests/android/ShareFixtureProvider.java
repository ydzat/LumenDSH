package local.sliverwolf.assistant.tests;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Test-only real content provider for grant, bounded-copy and unavailable-input checks. */
public final class ShareFixtureProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "text/plain";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];
        for(int i=0;i<columns.length;i++)row[i]=columns[i].equals(OpenableColumns.DISPLAY_NAME)?"controlled-share.txt":19;
        cursor.addRow(row);return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(!mode.equals("r"))throw new FileNotFoundException("Read-only fixture");
        String kind=uri.getLastPathSegment();
        if("unreadable".equals(kind))throw new FileNotFoundException("Controlled unavailable attachment");
        if(!"normal".equals(kind)&&!"large".equals(kind))throw new FileNotFoundException("Unknown fixture");
        File file=new File(getContext().getCacheDir(),"share-"+kind+".txt");
        try(RandomAccessFile output=new RandomAccessFile(file,"rw")) {
            output.setLength(0);output.write("M45_SHARED_FILE_OK\n".getBytes("UTF-8"));
            if("large".equals(kind))output.setLength(17L*1024*1024);
        }catch(IOException error){throw new FileNotFoundException(error.getMessage());}
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException();}
}
