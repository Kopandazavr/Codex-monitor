package dev.kopandazavr.codexmonitor;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Groups runtime watchdog instances under their shared role-level controls. */
final class ProcessRoleGroup {
    final String roleKey;
    final List<CalendarProcess> processes;

    private ProcessRoleGroup(String roleKey, List<CalendarProcess> processes) {
        this.roleKey = roleKey == null ? "" : roleKey;
        this.processes = Collections.unmodifiableList(new ArrayList<>(processes));
    }

    CalendarProcess representative() {
        return processes.isEmpty() ? null : processes.get(0);
    }

    static List<ProcessRoleGroup> group(Context context, List<CalendarProcess> processes) {
        Map<String, List<CalendarProcess>> grouped = new LinkedHashMap<>();
        if (processes != null) {
            for (CalendarProcess process : processes) {
                if (process == null) continue;
                String roleKey = IdleProcessState.roleKey(context, process);
                if (roleKey == null || roleKey.isEmpty()) {
                    roleKey = "process:" + process.identity();
                }
                grouped.computeIfAbsent(roleKey, ignored -> new ArrayList<>()).add(process);
            }
        }
        List<ProcessRoleGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<CalendarProcess>> entry : grouped.entrySet()) {
            result.add(new ProcessRoleGroup(entry.getKey(), entry.getValue()));
        }
        return result;
    }
}
