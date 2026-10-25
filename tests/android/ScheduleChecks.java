package local.sliverwolf.assistant;

import java.io.File;
import java.nio.file.Files;

/** Real registry I/O with controlled times; this does not prove Android alarm delivery. */
public final class ScheduleChecks {
    private static void require(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        File root=Files.createTempDirectory(new File(args[0]).toPath(),"schedule-check-").toFile();
        ScheduledTaskStore store=new ScheduledTaskStore(root);
        ScheduledTaskStore.Task once=new ScheduledTaskStore.Task("one",1000,"once",0,"Controlled prompt").withSession("existing-session");
        store.put(once);store=new ScheduledTaskStore(root);
        require(store.get("one").sessionId.equals("existing-session"),"Session survives restart");
        require(store.claim("one",1000,999)==null,"Never early");
        store.pause("one",true);require(store.claim("one",1000,1001)==null,"Paused occurrence not consumed");
        store.pause("one",false);require(store.claim("one",1000,1001)!=null,"Due occurrence claimed");
        store=new ScheduledTaskStore(root);
        require(store.claim("one",1000,1002)==null,"Restart cannot replay claimed occurrence");
        require(store.isPaused("one")&&store.deliveredAt("one")==1000,"Once retained with durable outcome fence");
        store.put(once.at(2000));store.pause("one",false);
        require(store.claim("one",1000,2001)==null,"Old PendingIntent cannot consume rescheduled occurrence");
        require(store.claim("one",2000,2001)!=null,"Explicit reschedule can run");
        store.cancel("one");require(store.get("one")==null&&store.claim("one",2000,3000)==null,"Cancelled occurrence fenced");
        ScheduledTaskStore.Task recurring=new ScheduledTaskStore.Task("repeat",1000,"interval",1,"Repeat").withSession("same-session");
        store.put(recurring);require(store.claim("repeat",1000,125000)!=null,"Late repeating occurrence claimed once");
        require(store.get("repeat").triggerAt==181000,"Missed intervals coalesced");
        require(store.get("repeat").sessionId.equals("same-session"),"Recurring target preserved");
        System.out.println("SCHEDULE_STORE_CHECKS_PASS (12 assertions; local registry, not Android delivery)");
    }
}
