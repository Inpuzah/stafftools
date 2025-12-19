package com.inpuzah.stafftools.managers;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Appeal;
import com.inpuzah.stafftools.database.models.AppealStatus;
import com.inpuzah.stafftools.database.models.Punishment;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AppealManager {

    private final StaffToolsPlugin plugin;
    private final Map<UUID, Long> appealCooldowns;

    public AppealManager(StaffToolsPlugin plugin) {
        this.plugin = plugin;
        this.appealCooldowns = new HashMap<>();
    }

    public CompletableFuture<Appeal> createAppeal(Appeal appeal) {
        return CompletableFuture.supplyAsync(() -> {
            // Check if appeals are enabled
            if (!plugin.getConfig().getBoolean("punishment.appeals.enabled", true)) {
                return null;
            }

            // Check cooldown
            long cooldownHours = plugin.getConfig().getLong("punishment.appeals.cooldown-hours", 24);
            Long lastAppeal = appealCooldowns.get(appeal.getPlayerUuid());

            if (lastAppeal != null && System.currentTimeMillis() - lastAppeal < cooldownHours * 60 * 60 * 1000) {
                return null; // On cooldown
            }

            // Verify punishment exists and is appealable
            Punishment punishment = plugin.getPunishmentManager().getPunishmentById(appeal.getPunishmentId()).join();
            if (punishment == null || !punishment.isActive()) {
                return null;
            }

            // Check if punishment type is appealable
            List<String> appealableTypes = plugin.getConfig().getStringList("punishment.appeals.allow-appeal-types");
            if (!appealableTypes.contains(punishment.getType().name())) {
                return null;
            }

            // Check if already appealed
            if (hasActiveAppeal(appeal.getPunishmentId())) {
                return null;
            }

            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = """
                    INSERT INTO punishment_appeals (punishment_id, player_uuid, player_name, 
                    appeal_text, timestamp, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                """;

                try (PreparedStatement stmt = conn.prepareStatement(query, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    stmt.setInt(1, appeal.getPunishmentId());
                    stmt.setString(2, appeal.getPlayerUuid().toString());
                    stmt.setString(3, appeal.getPlayerName());
                    stmt.setString(4, appeal.getAppealText());
                    stmt.setLong(5, appeal.getTimestamp());
                    stmt.setString(6, appeal.getStatus().name());

                    stmt.executeUpdate();

                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            appeal.setId(rs.getInt(1));
                        }
                    }
                }

                // Set cooldown
                appealCooldowns.put(appeal.getPlayerUuid(), System.currentTimeMillis());

                // Notify staff
                if (plugin.getConfig().getBoolean("punishment.appeals.auto-notify-staff", true)) {
                    notifyStaff(appeal, punishment);
                }

                // Send to Discord
                plugin.getDiscordManager().sendAppealNotification(appeal, punishment);

                // Log to audit
                plugin.getAuditManager().logAction(
                        appeal.getPlayerUuid(),
                        appeal.getPlayerName(),
                        "APPEAL_CREATED",
                        null,
                        null,
                        "Appeal for punishment #" + appeal.getPunishmentId()
                );

                return appeal;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to create appeal: " + e.getMessage());
                return null;
            }
        });
    }

    private boolean hasActiveAppeal(int punishmentId) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "SELECT COUNT(*) FROM punishment_appeals WHERE punishment_id = ? AND status = 'PENDING'";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, punishmentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check active appeal: " + e.getMessage());
        }
        return false;
    }

    private void notifyStaff(Appeal appeal, Punishment punishment) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            String message = MessageUtil.colorize(
                    "&7[&b&lAPPEAL&7] &e" + appeal.getPlayerName() +
                            " &7appealed their &c" + punishment.getType().name() +
                            " &7for: &f" + punishment.getReason()
            );

            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff != null && staff.hasPermission("stafftools.appeal.notify")) {
                    staff.sendMessage(message);
                    staff.sendMessage(MessageUtil.colorize("&7Use &e/appeals &7to review appeals"));
                }
            }
        });
    }

    public CompletableFuture<List<Appeal>> getAppeals(AppealStatus status) {
        return CompletableFuture.supplyAsync(() -> {
            List<Appeal> appeals = new ArrayList<>();

            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = status == null ?
                        "SELECT * FROM punishment_appeals ORDER BY timestamp DESC LIMIT 100" :
                        "SELECT * FROM punishment_appeals WHERE status = ? ORDER BY timestamp DESC LIMIT 100";

                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    if (status != null) {
                        stmt.setString(1, status.name());
                    }

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            appeals.add(parseAppeal(rs));
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get appeals: " + e.getMessage());
            }

            return appeals;
        });
    }

    public CompletableFuture<Boolean> reviewAppeal(int appealId, UUID staffUuid, String staffName,
                                                   AppealStatus decision, String reviewNote) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = """
                    UPDATE punishment_appeals SET status = ?, reviewed_by = ?, reviewed_by_name = ?,
                    reviewed_at = ?, review_note = ? WHERE id = ?
                """;

                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, decision.name());
                    stmt.setString(2, staffUuid.toString());
                    stmt.setString(3, staffName);
                    stmt.setLong(4, System.currentTimeMillis());
                    stmt.setString(5, reviewNote);
                    stmt.setInt(6, appealId);

                    int affected = stmt.executeUpdate();

                    if (affected > 0) {
                        // If approved, remove the punishment
                        if (decision == AppealStatus.APPROVED) {
                            Appeal appeal = getAppeal(appealId).join();
                            if (appeal != null) {
                                plugin.getPunishmentManager().removePunishment(
                                        appeal.getPunishmentId(),
                                        staffUuid,
                                        staffName,
                                        "Appeal approved: " + reviewNote
                                );

                                // Notify player if online
                                Player player = Bukkit.getPlayer(appeal.getPlayerUuid());
                                if (player != null && player.isOnline()) {
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        MessageUtil.sendMessage(player, "&a&lYour appeal has been approved!");
                                        MessageUtil.sendMessage(player, "&7Review note: &f" + reviewNote);
                                    });
                                }
                            }
                        }

                        // Log to audit
                        plugin.getAuditManager().logAction(
                                staffUuid,
                                staffName,
                                "APPEAL_REVIEWED",
                                null,
                                null,
                                String.format("Appeal #%d - Decision: %s, Note: %s", appealId, decision, reviewNote)
                        );

                        return true;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to review appeal: " + e.getMessage());
            }
            return false;
        });
    }

    public CompletableFuture<Appeal> getAppeal(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = "SELECT * FROM punishment_appeals WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setInt(1, id);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return parseAppeal(rs);
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get appeal: " + e.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<List<Appeal>> getPlayerAppeals(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Appeal> appeals = new ArrayList<>();

            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = "SELECT * FROM punishment_appeals WHERE player_uuid = ? ORDER BY timestamp DESC";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, playerUuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            appeals.add(parseAppeal(rs));
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get player appeals: " + e.getMessage());
            }

            return appeals;
        });
    }

    private Appeal parseAppeal(ResultSet rs) throws SQLException {
        return new Appeal(
                rs.getInt("id"),
                rs.getInt("punishment_id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                rs.getString("appeal_text"),
                rs.getLong("timestamp"),
                AppealStatus.valueOf(rs.getString("status")),
                rs.getString("reviewed_by") != null ? UUID.fromString(rs.getString("reviewed_by")) : null,
                rs.getString("reviewed_by_name"),
                rs.getLong("reviewed_at") == 0 ? null : rs.getLong("reviewed_at"),
                rs.getString("review_note")
        );
    }
}