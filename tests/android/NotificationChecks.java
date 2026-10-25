package local.sliverwolf.assistant;

import java.io.File;
import java.nio.file.Files;
import org.json.JSONArray;

/** Runs the adapted archive on Android with controlled temporary records. */
public final class NotificationChecks {
    public static void main(String[] args)throws Exception {
        File root=Files.createTempDirectory(new File(args[0]).toPath(),"notification-check-").toFile();
        for(int i=0;i<102;i++)NotificationArchive.save(root,"Test "+i,"Controlled text","test-session");
        JSONArray records=NotificationArchive.list(root);
        if(records.length()!=100)throw new AssertionError("Bounded archive retention");
        for(int i=0;i<records.length();i++)if(!records.getJSONObject(i).getString("session").equals("test-session"))
            throw new AssertionError("Original session reference retained");
        System.out.println("NOTIFICATION_ARCHIVE_CHECKS_PASS (Android private temporary records)");
    }
}
