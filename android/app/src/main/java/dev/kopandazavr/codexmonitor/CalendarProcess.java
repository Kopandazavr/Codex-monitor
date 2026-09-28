package dev.kopandazavr.codexmonitor;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CalendarProcess {
    static final String WATCHDOG_PREFIX="GPT_WATCHDOG|urgent|";
    static final String METADATA_VERSION="v1";
    static final long ACTIVE_WORK_WINDOW_MS=27L*60_000L;
    private static final Pattern METADATA_PAIR=Pattern.compile(
            "(?is)(?:^|\\s)([a-z0-9_.-]+)\\s*=\\s*(.*?)(?=(?:\\s+[a-z0-9_.-]+\\s*=)|$)");

    final long eventId,beginMillis,endMillis,providerUpdatedMillis;
    final String project,projectShort,role,topic;
    final boolean directSource;

    CalendarProcess(long eventId,long beginMillis,long endMillis,String project,String role,String topic){
        this(eventId,beginMillis,endMillis,0L,project,"",role,topic,false);
    }
    private CalendarProcess(long eventId,long beginMillis,long endMillis,long providerUpdatedMillis,
            String project,String projectShort,String role,String topic,boolean directSource){
        this.eventId=eventId;this.beginMillis=beginMillis;this.endMillis=endMillis;
        this.providerUpdatedMillis=Math.max(0L,providerUpdatedMillis);
        this.project=clean(project);this.projectShort=clean(projectShort);
        this.role=clean(role);this.topic=clean(topic);this.directSource=directSource;
    }

    static CalendarProcess fromEvent(long id,String title,String description,long begin,long end){
        return fromEvent(id,title,description,begin,end,0L,false);
    }
    static CalendarProcess fromDirectEvent(long id,String title,String description,long begin,long end){
        return fromDirectEvent(id,title,description,begin,end,0L);
    }
    static CalendarProcess fromDirectEvent(long id,String title,String description,long begin,long end,long updated){
        return fromEvent(id,title,description,begin,end,updated,true);
    }
    private static CalendarProcess fromEvent(long id,String title,String description,long begin,long end,
            long updated,boolean direct){
        if(!rejectionReason(title,description,begin,end).isEmpty())return null;
        Map<String,String> m=parseMetadata(description);
        return new CalendarProcess(id,begin,end,updated,m.get("project"),m.get("project_short"),
                m.get("role"),m.get("topic"),direct);
    }

    static String rejectionReason(String title,String description,long begin,long end){
        if(title==null||!title.startsWith(WATCHDOG_PREFIX))return "not_watchdog";
        if(begin<=0L||end<=begin)return "invalid_time";
        if(!hasSupportedMarker(description))return "missing_or_invalid_marker";
        Map<String,String> m=parseMetadata(description);
        if(!hasCanonicalProject(m.get("project")))return "missing_or_invalid_project";
        if(!hasCanonicalRole(m.get("role")))return "missing_or_invalid_role";
        return "";
    }
    static boolean isCanonicalIdentity(String project,String role){
        return hasCanonicalProject(project)&&hasCanonicalRole(role);
    }
    static boolean hasCanonicalProject(String project){return hasCanonicalValue(project);}
    static boolean hasSupportedMarker(String d){
        if(d==null||d.trim().isEmpty())return false;
        Matcher m=METADATA_PAIR.matcher(normalizeMetadata(d));
        while(m.find()){
            String k=clean(m.group(1)).toLowerCase(Locale.ROOT),v=clean(m.group(2));
            if(("codex_monitor_watchdog".equals(k)||"codex_meter_watchdog".equals(k))
                    &&METADATA_VERSION.equalsIgnoreCase(v))return true;
        }
        return false;
    }
    static String metadataValueForDiagnostics(String d,String requested){
        String target=clean(requested).toLowerCase(Locale.ROOT);
        if(target.isEmpty()||d==null||d.trim().isEmpty())return "";
        Matcher m=METADATA_PAIR.matcher(normalizeMetadata(d));
        while(m.find())if(target.equals(clean(m.group(1)).toLowerCase(Locale.ROOT)))return clean(m.group(2));
        return "";
    }
    static String markerValueForDiagnostics(String d){
        String v=metadataValueForDiagnostics(d,"codex_monitor_watchdog");
        return v.isEmpty()?metadataValueForDiagnostics(d,"codex_meter_watchdog"):v;
    }
    static Map<String,String> parseMetadata(String d){
        Map<String,String> values=new LinkedHashMap<>();
        if(d==null||d.trim().isEmpty())return values;
        Matcher m=METADATA_PAIR.matcher(normalizeMetadata(d)); boolean supported=false;
        while(m.find()){
            String k=clean(m.group(1)).toLowerCase(Locale.ROOT),v=clean(m.group(2));
            if("codex_monitor_watchdog".equals(k)||"codex_meter_watchdog".equals(k)){
                supported=METADATA_VERSION.equalsIgnoreCase(v);
            }else if(!v.isEmpty())values.put(k,v);
        }
        return supported?values:new LinkedHashMap<>();
    }

    long workStartMillis(){return Math.max(0L,beginMillis-ACTIVE_WORK_WINDOW_MS);}
    boolean isWorkRunning(long nowMillis){return isVisibleActive(nowMillis);}
    /** BEGIN is the only lifecycle deadline; Calendar END is carrier-only metadata. */
    boolean isWatchdogActive(long nowMillis){return isVisibleActive(nowMillis);}
    boolean isVisibleActive(long nowMillis){return nowMillis>=workStartMillis()&&nowMillis<beginMillis;}
    long remainingMillis(long nowMillis){return Math.max(0L,beginMillis-nowMillis);}
    int remainingPercent(long nowMillis){
        long start=workStartMillis();
        if(nowMillis<=start)return 100;
        if(nowMillis>=beginMillis)return 0;
        long duration=beginMillis-start,remaining=beginMillis-nowMillis;
        if(duration<=0L)return 0;
        return (int)Math.max(0L,Math.min(100L,Math.round((remaining*100.0d)/duration)));
    }
    int elapsedPercent(long nowMillis){
        long start=workStartMillis();
        if(nowMillis<=start)return 0;
        if(nowMillis>=beginMillis)return 100;
        long duration=beginMillis-start,elapsed=nowMillis-start;
        if(duration<=0L)return 100;
        return (int)Math.max(0L,Math.min(100L,Math.round((elapsed*100.0d)/duration)));
    }

    String displayLabel(){return displayIdentity(role,project,projectShort,topic);}
    static String displayIdentity(String role,String project,String topic){
        return displayIdentity(role,project,"",topic);
    }
    static String displayIdentity(String role,String project,String projectShort,String topic){
        String r=clean(role),p=clean(project),compact=valueOr(projectShort,p);
        if(hasCanonicalRole(r)){
            if(!compact.isEmpty()&&!compact.equalsIgnoreCase(r))return compact+" — "+r;
            return r;
        }
        if(!compact.isEmpty())return compact;
        String t=clean(topic);return t.isEmpty()?"Active process":t;
    }
    static boolean hasCanonicalRole(String role){return hasCanonicalValue(role);}
    private static boolean hasCanonicalValue(String value){
        String v=clean(value);
        return !v.isEmpty()&&!"unknown".equalsIgnoreCase(v)&&!"null".equalsIgnoreCase(v)
                &&!"none".equalsIgnoreCase(v)&&!"n/a".equalsIgnoreCase(v)&&!"-".equals(v);
    }
    /** Stable across an in-place Calendar reschedule of the same provider event. */
    String identity(){return String.valueOf(eventId);}

    private static String normalizeMetadata(String d){
        return d.replace('\r',' ').replaceAll("(?is)<br\\s*/?>"," ")
                .replaceAll("(?is)<[^>]+>"," ").replace("&nbsp;"," ").replace("&#160;"," ");
    }
    private static String valueOr(String preferred,String fallback){
        String p=clean(preferred);return p.isEmpty()?clean(fallback):p;
    }
    private static String clean(String v){return v==null?"":v.trim();}
}
