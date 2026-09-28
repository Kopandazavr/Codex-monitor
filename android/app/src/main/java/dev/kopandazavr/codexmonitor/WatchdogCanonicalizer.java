package dev.kopandazavr.codexmonitor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class WatchdogCanonicalizer {
    static final class Memory {
        long winnerEventId,winnerBeginMillis,winnerUpdatedMillis;
        final Set<Long> shadowEventIds=new HashSet<>();
    }
    private WatchdogCanonicalizer(){}

    static List<CalendarProcess> select(List<CalendarProcess> input,Map<String,Memory> memoryByKey){
        List<CalendarProcess> result=new ArrayList<>();
        if(input==null||input.isEmpty())return result;
        Map<String,List<CalendarProcess>> groups=new LinkedHashMap<>();
        for(CalendarProcess p:input){
            if(p==null)continue; String key=logicalKey(p); if(key.isEmpty())continue;
            groups.computeIfAbsent(key,x->new ArrayList<>()).add(p);
        }
        for(Map.Entry<String,List<CalendarProcess>> e:groups.entrySet()){
            Memory memory=memoryByKey.computeIfAbsent(e.getKey(),x->new Memory());
            List<CalendarProcess> allowed=new ArrayList<>();
            for(CalendarProcess p:e.getValue()){
                boolean shadow=memory.shadowEventIds.contains(p.eventId);
                boolean newerReschedule=shadow&&p.beginMillis>memory.winnerBeginMillis;
                if(!shadow||newerReschedule)allowed.add(p);
            }
            if(allowed.isEmpty())continue;
            CalendarProcess winner=allowed.get(0);
            for(int i=1;i<allowed.size();i++)if(isNewer(allowed.get(i),winner))winner=allowed.get(i);
            for(CalendarProcess p:e.getValue())if(p.eventId!=winner.eventId)memory.shadowEventIds.add(p.eventId);
            memory.shadowEventIds.remove(winner.eventId);
            memory.winnerEventId=winner.eventId;memory.winnerBeginMillis=winner.beginMillis;
            memory.winnerUpdatedMillis=winner.providerUpdatedMillis;
            result.add(winner);
        }
        result.sort((a,b)->{int c=Long.compare(a.beginMillis,b.beginMillis);
            return c!=0?c:Long.compare(a.eventId,b.eventId);});
        return result;
    }

    static String logicalKey(CalendarProcess p){
        if(p==null)return "";
        String project=ProjectProfileRules.normalizeAlias(p.project);
        String role=ProjectProfileRules.normalizeAlias(p.role);
        return project.isEmpty()||role.isEmpty()?"":project+"\u001f"+role;
    }
    private static boolean isNewer(CalendarProcess candidate,CalendarProcess current){
        if(candidate.beginMillis!=current.beginMillis)return candidate.beginMillis>current.beginMillis;
        if(candidate.providerUpdatedMillis!=current.providerUpdatedMillis)
            return candidate.providerUpdatedMillis>current.providerUpdatedMillis;
        return candidate.eventId>current.eventId;
    }
}
