package com.inpuzah.stafftools.managers;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Report;
import com.inpuzah.stafftools.database.models.ReportStatus;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement; // <-- needed for RETURN_GENERATED_KEYS
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ReportManager {

    private final StaffToolsPlugin plugin;
    private final Map<UUID, Long> reportCooldowns;

    public ReportManager(StaffToolsPlugin plugin) {
        this.plugin = plugin;
        this.reportCooldowns = new HashMap<>();
        startAutoCloseTask();
    }

    private void startAutoCloseTask() {
        int autoCloseDays = plugin.getConfig().getInt("reports.auto-close-days", 7);
        if (autoCloseDays <= 0) return;

        // Run once per day
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long cutoffTime = System.currentTimeMillis() - (autoCloseDays * 24L * 60 * 60 * 1000);

            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = "UPDATE reports SET status = 'DISMISSED' WHERE status = 'OPEN' AND timestamp < ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setLong(1, cutoffTime);
                    int closed = stmt.executeUpdate();
                    if (closed > 0) {
                        plugin.getLogger().info("Auto-closed " + closed + " old reports");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to auto-close reports: " + e.getMessage());
            }
        }, 20L * 60 * 60 * 24, 20L * 60 * 60 * 24);
    }

    public CompletableFuture<Report> createReport(Report report) {
        return CompletableFuture.supplyAsync(() -> {
            // Cooldown
            long cooldown = plugin.getConfig().getLong("reports.report-cooldown", 60) * 1000;
            Long lastReport = reportCooldowns.get(report.getReporterUuid());
            if (lastReport != null && System.currentTimeMillis() - lastReport < cooldown) {
                return null; // still on cooldown
            }
            if (report.getStatus() == null) report.setStatus(ReportStatus.OPEN);

            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = """
                    INSERT INTO reports (reporter_uuid, reporter_name, reported_uuid, reported_name,
                    reason, timestamp, status, server_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

                try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, report.getReporterUuid().toString());
                    stmt.setString(2, report.getReporterName());
                    stmt.setString(3, report.getReportedUuid().toString());
                    stmt.setString(4, report.getReportedName());
                    stmt.setString(5, report.getReason());
                    stmt.setLong(6, report.getTimestamp());
                    stmt.setString(7, report.getStatus().name());
                    stmt.setString(8, Bukkit.getServer().getName());
                    stmt.executeUpdate();

                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) report.setId(rs.getInt(1));
                    }
                }

                // cooldown + notify + audit + discord
                reportCooldowns.put(report.getReporterUuid(), System.currentTimeMillis());
                if (plugin.getConfig().getBoolean("reports.notify-staff", true)) {
                    notifyStaff(report);
                }
                plugin.getDiscordManager().sendReportNotification(report);
                plugin.getAuditManager().logAction(
                        report.getReporterUuid(), report.getReporterName(), "REPORT_CREATED",
                        report.getReportedUuid(), report.getReportedName(), "Reason: " + report.getReason()
                );

                return report;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to create report: " + e.getMessage());
                return null;
            }
        });
    }

    private void notifyStaff(Report report) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            String message = MessageUtil.colorize(
                    "&7[&c&lREPORT&7] &e" + report.getReporterName() +
                            " &7reported &e" + report.getReportedName() +
                            " &7for: &f" + report.getReason()
            );
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff != null && staff.hasPermission("stafftools.report.notify")) {
                    staff.sendMessage(message);
                    staff.sendMessage(MessageUtil.colorize("&7Use &e/reports &7to view and manage reports"));
                }
            }
        });
    }

    public CompletableFuture<List<Report>> getReports(ReportStatus status) {
        return CompletableFuture.supplyAsync(() -> {
            List<Report> reports = new ArrayList<>();
            String query = status == null
                    ? "SELECT * FROM reports ORDER BY timestamp DESC LIMIT 100"
                    : "SELECT * FROM reports WHERE status = ? ORDER BY timestamp DESC LIMIT 100";

            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                if (status != null) stmt.setString(1, status.name());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) reports.add(parseReport(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get reports: " + e.getMessage());
            }
            return reports;
        });
    }

    public CompletableFuture<List<Report>> getOpenReports() {
        return getReports(ReportStatus.OPEN);
    }

    public CompletableFuture<Boolean> handleReport(int reportId, UUID staffUuid, String staffName,
                                                   ReportStatus newStatus, String note) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("""
                     UPDATE reports SET status = ?, handled_by = ?, handled_by_name = ?,
                     handled_at = ?, handler_note = ? WHERE id = ?
                 """)) {
                stmt.setString(1, newStatus.name());
                stmt.setString(2, staffUuid.toString());
                stmt.setString(3, staffName);
                stmt.setLong(4, System.currentTimeMillis());
                stmt.setString(5, note);
                stmt.setInt(6, reportId);
                int affected = stmt.executeUpdate();

                if (affected > 0) {
                    plugin.getAuditManager().logAction(
                            staffUuid, staffName, "REPORT_HANDLED",
                            null, null,
                            "Report #" + reportId + " - Status: " + newStatus + " - Note: " + note
                    );
                    return true;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to handle report: " + e.getMessage());
            }
            return false;
        });
    }

    public CompletableFuture<Report> getReport(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM reports WHERE id = ?")) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return parseReport(rs);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get report: " + e.getMessage());
            }
            return null;
        });
    }

    private Report parseReport(ResultSet rs) throws SQLException {
        return new Report(
                rs.getInt("id"),
                UUID.fromString(rs.getString("reporter_uuid")),
                rs.getString("reporter_name"),
                UUID.fromString(rs.getString("reported_uuid")),
                rs.getString("reported_name"),
                rs.getString("reason"),
                rs.getLong("timestamp"),
                ReportStatus.valueOf(rs.getString("status")),
                rs.getString("handled_by") != null ? UUID.fromString(rs.getString("handled_by")) : null,
                rs.getString("handled_by_name"),
                rs.getObject("handled_at") == null ? null : rs.getLong("handled_at"),
                rs.getString("handler_note"),
                rs.getString("server_name")
        );
    }
}
