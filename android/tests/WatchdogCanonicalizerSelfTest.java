package dev.kopandazavr.codexmonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WatchdogCanonicalizerSelfTest {
    private WatchdogCanonicalizerSelfTest(){}
    public static void main(String[] args){
        Map<String,WatchdogCanonicalizer.Memory> memory=new HashMap<>();
        CalendarProcess early=p(10L,100_000L,1_000L,"old");
        CalendarProcess late=p(11L,110_000L,900L,"new");
        List<CalendarProcess> first=WatchdogCanonicalizer.select(list(early,late),memory);
        require(first.size()==1&&first.get(0).eventId==11L,"greatest BEGIN wins");
        require(WatchdogCanonicalizer.select(list(early),memory).isEmpty(),
                "known older shadow cannot revive");
        CalendarProcess moved=p(10L,120_000L,2_000L,"rescheduled");
        List<CalendarProcess> rescheduled=WatchdogCanonicalizer.select(list(moved),memory);
        require(rescheduled.size()==1&&rescheduled.get(0).eventId==10L,
                "shadow with newer BEGIN becomes valid reschedule");

        Map<String,WatchdogCanonicalizer.Memory> tieMemory=new HashMap<>();
        List<CalendarProcess> tie=WatchdogCanonicalizer.select(
                list(p(20L,200_000L,1_000L,"a"),p(21L,200_000L,2_000L,"b")),tieMemory);
        require(tie.size()==1&&tie.get(0).eventId==21L,"updated marker breaks BEGIN tie");

        Map<String,WatchdogCanonicalizer.Memory> idMemory=new HashMap<>();
        List<CalendarProcess> idTie=WatchdogCanonicalizer.select(
                list(p(30L,300_000L,0L,"a"),p(31L,300_000L,0L,"b")),idMemory);
        require(idTie.size()==1&&idTie.get(0).eventId==31L,"provider id final tie-break");
        System.out.println("Watchdog canonicalization + shadow suppression PASS");
    }
    private static CalendarProcess p(long id,long begin,long updated,String topic){
        return CalendarProcess.fromDirectEvent(id,"GPT_WATCHDOG|urgent|Codex Monitor",
                "codex_monitor_watchdog=v1 project=Codex Monitor role=Main Agent topic="+topic,
                begin,begin+60_000L,updated);
    }
    private static List<CalendarProcess> list(CalendarProcess...values){
        List<CalendarProcess> r=new ArrayList<>();for(CalendarProcess v:values)r.add(v);return r;
    }
    private static void require(boolean c,String label){if(!c)throw new AssertionError(label);}
}
