package com.inpuzah.stafftools.managers;

import com.inpuzah.stafftools.StaffToolsPlugin;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class AuditManager {

    private final StaffToolsPlugin plugin;
    private final ExecutorService db;

    public AuditManager(StaffToolsPlugin plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager().getExecutor();
    }

    public CompletableFuture<Void> logAction(UUID staffUuid, String staffName, String action,
            UUID targetUuid, String targetName, String details) {
        return CompletableFuture.runAsync(() -> {
            if (!plugin.getConfig().getBoolean("audit.enabled", true)) {
                plugin.getLogger().info("[Audit] Skipped (disabled): " + action);
                return;
            }

            List<String> logActions = plugin.getConfig().getStringList("audit.log-actions");
            if (!logActions.contains(action)) {
                plugin.getLogger().info("[Audit] Skipped (not in log-actions): " + action);
                return;
            }

            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = """
                            INSERT INTO audit_log (staff_uuid, staff_name, action, target_uuid,
                            target_name, details, timestamp, server_name)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """;

                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, staffUuid.toString());
                    stmt.setString(2, staffName);
                    stmt.setString(3, action);
                    stmt.setString(4, targetUuid != null ? targetUuid.toString() : null);
                    stmt.setString(5, targetName);
                    stmt.setString(6, details);
                    stmt.setLong(7, System.currentTimeMillis());
                    stmt.setString(8, Bukkit.getServer().getName());

                    stmt.executeUpdate();
                    plugin.getLogger().info("[Audit] Logged: " + action + " by " + staffName + " - " + details);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Audit] Failed to log action: " + e.getMessage());
                e.printStackTrace();
            }
        }, db);
    }

    public void cleanupOldLogs() {
        int retentionDays = plugin.getConfig().getInt("audit.retention-days", 90);
        if (retentionDays <= 0) {
            return; // Keep forever
        }

        CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000);

                String query = "DELETE FROM audit_log WHERE timestamp < ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setLong(1, cutoffTime);
                    int deleted = stmt.executeUpdate();

                    if (deleted > 0) {
                        plugin.getLogger().info("[Audit] Cleaned up " + deleted + " old audit log entries");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Audit] Failed to cleanup old audit logs: " + e.getMessage());
            }
        }, db);
    }
}