package com.neoms;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import static com.neoms.Engine.*;

@Service
public class ShiftLogService {
    private final Store db;
    ShiftLogService(Store db) { this.db = db; }

    public Map<String, Object> sheet(String shiftId) {
        var shift = db.read(shiftId);
        require(s(shift, "kind").equals("shifts"), "请选择有效班次");
        var start = LocalDateTime.parse(s(shift, "startAt"));
        var end = LocalDateTime.parse(s(shift, "endAt"));
        String stationId = s(shift, "stationId");
        var events = new ArrayList<Map<String, Object>>();
        var defects = new ArrayList<Map<String, Object>>();
        var types = new LinkedHashMap<String, String>();
        types.put("notes", "值班记事"); types.put("commands", "调度命令");
        types.put("defects", "缺陷管理"); types.put("workTickets", "工作票");
        types.put("operationTickets", "操作票"); types.put("notices", "通知交代");
        for (var type : types.entrySet()) {
            for (var record : db.list(type.getKey())) {
                if (!s(record, "stationId").equals(stationId) || s(record, "createdAt").isBlank()) continue;
                var time = LocalDateTime.parse(s(record, "createdAt"));
                if (time.isBefore(start) || !time.isBefore(end)) continue;
                if (type.getKey().equals("notes") && !s(record, "shiftId").isBlank()
                        && !s(record, "shiftId").equals(shiftId)) continue;
                String content = switch (type.getKey()) {
                    case "notes" -> s(record, "description");
                    case "defects" -> "新增缺陷单" + s(record, "code");
                    default -> "新增" + type.getValue() + s(record, "code");
                };
                events.add(m("recordId", record.get("id"), "kind", type.getKey(), "type", type.getValue(),
                        "time", record.get("createdAt"), "content", content));
                if (type.getKey().equals("defects")) {
                    var defect = new LinkedHashMap<>(record);
                    defect.put("reporterName", person(record.getOrDefault("reporterId", record.get("createdBy"))));
                    defect.put("discoveredAt", record.getOrDefault("discoveredAt", record.get("createdAt")));
                    defect.put("approvalSubmittedAt", record.getOrDefault("approvalSubmittedAt", record.get("createdAt")));
                    defects.add(defect);
                }
            }
        }
        events.sort(Comparator.comparing((Map<String, Object> r) -> LocalDateTime.parse(s(r, "time"))).reversed()
                .thenComparing(r -> s(r, "recordId")));
        defects.sort(Comparator.comparing((Map<String, Object> r) -> LocalDateTime.parse(s(r, "discoveredAt"))).reversed());
        return m("shiftId", shiftId, "events", events, "defects", defects);
    }

    private String person(Object id) {
        if (id == null || id.toString().isBlank()) return "—";
        try { return s(db.read(id.toString()), "name"); }
        catch (IllegalArgumentException missing) { return id.toString(); }
    }
}
