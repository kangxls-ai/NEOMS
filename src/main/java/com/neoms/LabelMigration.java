package com.neoms;

import java.nio.file.*;

/** Cosmetic label upgrade. Keeps record IDs, relationships, states and credentials intact. */
final class LabelMigration {
    private LabelMigration() {}

    static String clean(String text) {
        return text.replace("演示管理员", "管理员")
            .replace("虚构演示数据", "业务数据")
            .replace("虚构演示票据", "作业票据")
            .replace("虚构演示内容", "作业内容")
            .replace("（原型演示）", "")
            .replace("（演示内容）", "")
            .replace("（演示设备）", "")
            .replace("（演示项目）", "")
            .replace("（演示）", "")
            .replace("预置模拟现场记录", "现场检查记录")
            .replace("预置模拟复测记录", "复测记录")
            .replace("模拟复测记录", "复测记录")
            .replace("模拟温度", "采样温度")
            .replace("演示占位", "待完善项目")
            .replace("本演示", "本系统")
            .replace("虚构", "")
            .replace("演示", "");
    }

    static void run(Store db, Path uploadDirectory) {
        for (var row : db.jdbc().queryForList("SELECT id,payload FROM neoms_records WHERE payload LIKE '%演示%'")) {
            String payload = clean(row.get("payload").toString());
            db.jdbc().update("UPDATE neoms_records SET payload=?,revision=revision+1 WHERE id=?", payload, row.get("id"));
        }
        for (var row : db.jdbc().queryForList("SELECT id,snapshot,actor,action_name FROM neoms_history WHERE snapshot LIKE '%演示%' OR actor LIKE '%演示%' OR action_name LIKE '%演示%'")) {
            db.jdbc().update("UPDATE neoms_history SET snapshot=?,actor=?,action_name=? WHERE id=?",
                clean(row.get("snapshot").toString()), clean(row.get("actor").toString()), clean(row.get("action_name").toString()), row.get("id"));
        }
        for (var row : db.jdbc().queryForList("SELECT id,display_name FROM neoms_users WHERE display_name LIKE '%演示%'")) {
            db.jdbc().update("UPDATE neoms_users SET display_name=? WHERE id=?", clean(row.get("display_name").toString()), row.get("id"));
        }
        Path root = uploadDirectory.toAbsolutePath().normalize();
        for (var row : db.jdbc().queryForList("SELECT id,original_name,disk_name,media_type FROM neoms_files WHERE original_name LIKE '%演示%'")) {
            String original = row.get("original_name").toString();
            db.jdbc().update("UPDATE neoms_files SET original_name=? WHERE id=?", clean(original), row.get("id"));
            // Rewrite only the known system-generated attachment, never arbitrary uploaded content.
            if (original.equals("演示票据终结记录.txt") && row.get("media_type").equals("text/plain")) {
                Path file = root.resolve(row.get("disk_name").toString()).normalize();
                if (file.startsWith(root) && Files.isRegularFile(file)) {
                    try { Files.writeString(file, "NEOMS 票据终结记录\n工作结束，现场恢复，人员撤离。\n"); }
                    catch (Exception ex) { throw new IllegalStateException("终结记录附件更新失败", ex); }
                }
            }
        }
    }
}
