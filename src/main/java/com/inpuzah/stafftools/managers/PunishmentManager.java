package com.inpuzah.stafftools.managers;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Punishment;
import com.inpuzah.stafftools.database.models.PunishmentType;
import com.inpuzah.stafftools.utils.MessageUtil;
import com.inpuzah.stafftools.utils.TimeUtil;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@SuppressWarnings("deprecation")
public class PunishmentManager {

    private final StaffToolsPlugin plugin;
    private final ExecutorService db;
    private final Map<UUID, Punishment> activeMutes = new ConcurrentHashMap<>();
    private final Map<UUID, Punishment> activeBans = new ConcurrentHashMap<>();

    public PunishmentManager(StaffToolsPlugin plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager().getExecutor();
        loadActivePunishmentsSync();
        startExpirationTask();
    }

    private boolean isActiveType(PunishmentType type) {
        return type == PunishmentType.MUTE || type == PunishmentType.BAN || type == PunishmentType.BUILDBAN;
    }

    private void loadActivePunishmentsSync() {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "SELECT * FROM punishments WHERE active = 1 AND (type = 'MUTE' OR type = 'BAN')");
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Punishment p = parsePunishment(rs);
                if (p.isExpired()) {
                    expirePunishmentAsync(p.getId());
                    unsyncVanillaBan(p);
                    continue;
                }
                if (p.getType() == PunishmentType.MUTE) {
                    activeMutes.put(p.getPlayerUuid(), p);
                } else if (p.getType() == PunishmentType.BAN) {
                    activeBans.put(p.getPlayerUuid(), p);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load active punishments: " + e.getMessage());
        }
    }

    private void startExpirationTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Punishment mute : new ArrayList<>(activeMutes.values())) {
                if (mute.isExpired()) {
                    expirePunishmentAsync(mute.getId());
                    activeMutes.remove(mute.getPlayerUuid());
                    Player player = Bukkit.getPlayer(mute.getPlayerUuid());
                    if (player != null && player.isOnline()) {
                        MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.punishment.unmuted"));
                    }
                }
            }
            for (Punishment ban : new ArrayList<>(activeBans.values())) {
                if (ban.isExpired()) {
                    expirePunishmentAsync(ban.getId());
                    activeBans.remove(ban.getPlayerUuid());
                    unsyncVanillaBan(ban);
                }
            }
        }, 20L * 10, 20L * 10);
    }

    private void unsyncVanillaBan(Punishment p) {
        if (p.getType() != PunishmentType.BAN || !plugin.getConfig().getBoolean("punishment.sync-with-vanilla", true))
            return;
        UUID uuid = p.getPlayerUuid();
        if (uuid == null)
            return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            ((ProfileBanList) Bukkit.getBanList(BanList.Type.PROFILE)).pardon(Bukkit.createPlayerProfile(uuid));
        });
    }

    public Punishment getActiveBan(UUID playerUuid) {
        if (playerUuid == null)
            return null;
        Punishment ban = activeBans.get(playerUuid);
        if (ban == null)
            return null;
        if (!ban.isExpired())
            return ban;
        activeBans.remove(playerUuid);
        expirePunishmentAsync(ban.getId());
        unsyncVanillaBan(ban);
        return null;
    }

    public boolean isPlayerBanned(UUID playerUuid) {
        return getActiveBan(playerUuid) != null;
    }

    public boolean isPlayerMuted(UUID playerUuid) {
        Punishment mute = activeMutes.get(playerUuid);
        if (mute == null)
            return false;
        if (!mute.isExpired())
            return true;
        activeMutes.remove(playerUuid);
        expirePunishmentAsync(mute.getId());
        return false;
    }

    public Punishment getActiveMute(UUID playerUuid) {
        Punishment mute = activeMutes.get(playerUuid);
        if (mute == null)
            return null;
        if (!mute.isExpired())
            return mute;
        activeMutes.remove(playerUuid);
        expirePunishmentAsync(mute.getId());
        return null;
    }

    public boolean hasActivePunishment(UUID playerUuid, PunishmentType type) {
        if (playerUuid == null)
            return false;
        if (type == PunishmentType.BAN)
            return getActiveBan(playerUuid) != null;
        if (type == PunishmentType.MUTE)
            return isPlayerMuted(playerUuid);
        if (type == PunishmentType.BUILDBAN)
            return plugin.getBuildBanManager().isBuildBanned(playerUuid);
        return false;
    }

    public String buildBanScreenMessage(Punishment punishment) {
        List<String> lines = plugin.getConfig().getStringList("messages.punishment.ban-screen");
        StringBuilder screen = new StringBuilder();
        String expiresText = punishment.isPermanent() ? "Never (Permanent)"
                : TimeUtil.formatDate(punishment.getExpiresAt());
        String durationRemaining = punishment.isPermanent() ? "Permanent"
                : TimeUtil.formatDurationMillis(punishment.getRemainingTime()) + " remaining";
        for (String line : lines) {
            String formatted = line
                    .replace("{reason}", punishment.getReason())
                    .replace("{duration}",
                            punishment.isPermanent() ? "Permanent" : TimeUtil.formatDuration(punishment.getDuration()))
                    .replace("{duration-remaining}", durationRemaining)
                    .replace("{date}", TimeUtil.formatDate(punishment.getTimestamp()))
                    .replace("{expires}", expiresText)
                    .replace("{id}", String.valueOf(punishment.getId()));
            screen.append(formatted).append("\n");
        }
        return screen.toString();
    }

    public CompletableFuture<Punishment> issuePunishment(Punishment punishment) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                if (isActiveType(punishment.getType())
                        && hasActivePunishment(punishment.getPlayerUuid(), punishment.getType())) {
                    return null;
                }
                if (isActiveType(punishment.getType())
                        && hasActiveInDb(conn, punishment.getPlayerUuid(), punishment.getType())) {
                    return null;
                }
                boolean shouldBeActive = isActiveType(punishment.getType());
                punishment.setActive(shouldBeActive);
                String query = """
                            INSERT INTO punishments (player_uuid, player_name, staff_uuid, staff_name,
                            type, reason, duration, timestamp, expires_at, active, server_name, ip_address)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """;
                try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, punishment.getPlayerUuid().toString());
                    stmt.setString(2, punishment.getPlayerName());
                    stmt.setString(3, punishment.getStaffUuid().toString());
                    stmt.setString(4, punishment.getStaffName());
                    stmt.setString(5, punishment.getType().name());
                    stmt.setString(6, punishment.getReason());
                    stmt.setLong(7, punishment.getDuration());
                    stmt.setLong(8, punishment.getTimestamp());
                    stmt.setObject(9, punishment.isPermanent() ? null : punishment.getExpiresAt());
                    stmt.setBoolean(10, shouldBeActive);
                    stmt.setString(11, Bukkit.getServer().getName());
                    stmt.setString(12, punishment.getIpAddress());
                    stmt.executeUpdate();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            punishment.setId(rs.getInt(1));
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to issue punishment: " + e.getMessage());
                return null;
            }
            if (punishment.isActive()) {
                if (punishment.getType() == PunishmentType.MUTE) {
                    activeMutes.put(punishment.getPlayerUuid(), punishment);
                }
                if (punishment.getType() == PunishmentType.BAN) {
                    activeBans.put(punishment.getPlayerUuid(), punishment);
                }
            }
            plugin.getLogger().info("[Punishment] Issued " + punishment.getType() + " to " + punishment.getPlayerName()
                    + " (ID: " + punishment.getId() + ")");
            applyPunishmentAsync(punishment);
            plugin.getAuditManager().logAction(
                    punishment.getStaffUuid(), punishment.getStaffName(), "PUNISHMENT_ISSUED",
                    punishment.getPlayerUuid(), punishment.getPlayerName(),
                    String.format("Type: %s, Reason: %s, Duration: %d", punishment.getType(), punishment.getReason(),
                            punishment.getDuration()));
            return punishment;
        }, db);
    }

    private boolean hasActiveInDb(Connection conn, UUID playerUuid, PunishmentType type) throws SQLException {
        if (!isActiveType(type))
            return false;
        String sql = "SELECT 1 FROM punishments WHERE player_uuid = ? AND type = ? AND active = 1 LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, type.name());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void applyPunishmentAsync(Punishment punishment) {
        Player player = Bukkit.getPlayer(punishment.getPlayerUuid());
        switch (punishment.getType()) {
            case WARN -> {
                if (player != null && player.isOnline()) {
                    String message = plugin.getConfig().getString("messages.punishment.warned").replace("{reason}",
                            punishment.getReason());
                    MessageUtil.sendMessage(player, message);
                }
            }
            case MUTE -> {
                if (player != null && player.isOnline()) {
                    String message = plugin.getConfig().getString("messages.punishment.muted")
                            .replace("{reason}", punishment.getReason())
                            .replace("{duration}", TimeUtil.formatDuration(punishment.getDuration()));
                    MessageUtil.sendMessage(player, message);
                }
            }
            case KICK -> {
                if (player != null && player.isOnline()) {
                    String message = plugin.getConfig().getString("messages.punishment.kicked").replace("{reason}",
                            punishment.getReason());
                    Bukkit.getScheduler().runTask(plugin, () -> player.kick(MessageUtil.component(message)));
                }
            }
            case BAN -> {
                if (plugin.getConfig().getBoolean("punishment.sync-with-vanilla", true)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        var profile = Bukkit.createPlayerProfile(punishment.getPlayerUuid(),
                                punishment.getPlayerName());
                        if (punishment.isPermanent()) {
                            ((ProfileBanList) Bukkit.getBanList(BanList.Type.PROFILE)).addBan(profile,
                                    punishment.getReason(), (Date) null, punishment.getStaffName());
                        } else {
                            ((ProfileBanList) Bukkit.getBanList(BanList.Type.PROFILE)).addBan(profile,
                                    punishment.getReason(), new Date(punishment.getExpiresAt()),
                                    punishment.getStaffName());
                        }
                    });
                }
                if (player != null && player.isOnline()) {
                    String banScreen = buildBanScreenMessage(punishment);
                    String kickReason = plugin.getConfig().getString("messages.punishment.ban-kick-reason",
                            "You are banned. See Discord for details.");
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        MessageUtil.sendMessage(player, banScreen);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> player.kick(MessageUtil.component(kickReason)),
                                2L);
                    });
                }
            }
            case BUILDBAN -> plugin.getBuildBanManager().issueBuildBan(punishment);
        }
        notifyStaff(punishment);
        if (punishment.getType() == PunishmentType.BUILDBAN) {
            plugin.getDiscordManager().sendBuildBanNotification(punishment);
        } else {
            plugin.getDiscordManager().sendPunishmentNotification(punishment);
        }
    }

    private void notifyStaff(Punishment punishment) {
        if (!plugin.getConfig().getBoolean("notifications.punishment-notifications", true))
            return;
        String format = plugin.getConfig().getString("notifications.format.punishment")
                .replace("{staff}", punishment.getStaffName())
                .replace("{player}", punishment.getPlayerName())
                .replace("{reason}", punishment.getReason());
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff != null && staff.hasPermission("stafftools.staff.notify")) {
                MessageUtil.sendMessage(staff, format);
            }
        }
    }

    public CompletableFuture<Boolean> removePunishment(int id, UUID removedBy, String removedByName, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                Punishment punishment = loadPunishment(conn, id);
                if (punishment == null || !punishment.isActive())
                    return false;
                if (!deactivatePunishment(conn, punishment, removedBy, removedByName, reason))
                    return false;
                postRemovalSideEffects(punishment);
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to remove punishment: " + e.getMessage());
                return false;
            }
        }, db);
    }

    private Punishment loadPunishment(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM punishments WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return parsePunishment(rs);
            }
        }
        return null;
    }

    private boolean deactivatePunishment(Connection conn, Punishment punishment, UUID removedBy, String removedByName,
            String reason) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE punishments SET active = 0, removed_by = ?, removed_at = ?, removed_reason = ? WHERE id = ?")) {
            stmt.setString(1, removedBy != null ? removedBy.toString() : null);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, reason);
            stmt.setInt(4, punishment.getId());
            stmt.executeUpdate();
        }
        if (punishment.getType() == PunishmentType.MUTE) {
            activeMutes.remove(punishment.getPlayerUuid());
        }
        if (punishment.getType() == PunishmentType.BAN) {
            activeBans.remove(punishment.getPlayerUuid());
        }
        if (punishment.getType() == PunishmentType.BUILDBAN) {
            plugin.getBuildBanManager().removeBuildBan(punishment.getPlayerUuid());
        }
        plugin.getAuditManager().logAction(removedBy, removedByName, "PUNISHMENT_REMOVED", punishment.getPlayerUuid(),
                punishment.getPlayerName(), String.format("Punishment ID: %d, Reason: %s", punishment.getId(), reason));
        return true;
    }

    private void postRemovalSideEffects(Punishment punishment) {
        if (punishment.getType() == PunishmentType.BAN
                && plugin.getConfig().getBoolean("punishment.sync-with-vanilla", true)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                ((ProfileBanList) Bukkit.getBanList(BanList.Type.PROFILE))
                        .pardon(Bukkit.createPlayerProfile(punishment.getPlayerUuid()));
            });
        }
    }

    private void expirePunishmentAsync(int id) {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                    PreparedStatement stmt = conn.prepareStatement("UPDATE punishments SET active = 0 WHERE id = ?")) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to expire punishment: " + e.getMessage());
            }
        }, db);
    }

    public CompletableFuture<List<Punishment>> getPlayerHistory(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> history = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                    PreparedStatement stmt = conn.prepareStatement(
                            "SELECT * FROM punishments WHERE player_uuid = ? ORDER BY timestamp DESC")) {
                stmt.setString(1, playerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next())
                        history.add(parsePunishment(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get player history: " + e.getMessage());
            }
            return history;
        }, db);
    }

    public CompletableFuture<List<Punishment>> getRecentPunishments(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> punishments = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                    PreparedStatement stmt = conn
                            .prepareStatement("SELECT * FROM punishments ORDER BY timestamp DESC LIMIT ?")) {
                stmt.setInt(1, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next())
                        punishments.add(parsePunishment(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get recent punishments: " + e.getMessage());
            }
            return punishments;
        }, db);
    }

    public CompletableFuture<Punishment> getPunishmentById(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                    PreparedStatement stmt = conn.prepareStatement("SELECT * FROM punishments WHERE id = ?")) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next())
                        return parsePunishment(rs);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get punishment: " + e.getMessage());
            }
            return null;
        }, db);
    }

    private Punishment parsePunishment(ResultSet rs) throws SQLException {
        return new Punishment(
                rs.getInt("id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                UUID.fromString(rs.getString("staff_uuid")),
                rs.getString("staff_name"),
                PunishmentType.valueOf(rs.getString("type")),
                rs.getString("reason"),
                rs.getLong("duration"),
                rs.getLong("timestamp"),
                rs.getObject("expires_at") == null ? null : rs.getLong("expires_at"),
                rs.getBoolean("active"),
                rs.getString("removed_by") != null ? UUID.fromString(rs.getString("removed_by")) : null,
                rs.getObject("removed_at") == null ? null : rs.getLong("removed_at"),
                rs.getString("removed_reason"),
                rs.getString("server_name"),
                rs.getString("ip_address"));
    }

    public CompletableFuture<Boolean> unbanByNameOrUuid(String idOrName, UUID staffUuid, String staffName,
            String reason) {
        return CompletableFuture.supplyAsync(() -> {
            UUID parsed = null;
            try {
                parsed = UUID.fromString(idOrName);
            } catch (IllegalArgumentException ignored) {
            }
            final String sql = (parsed != null)
                    ? "SELECT * FROM punishments WHERE player_uuid = ? AND type = 'BAN' AND active = 1 ORDER BY timestamp DESC LIMIT 1"
                    : "SELECT * FROM punishments WHERE LOWER(player_name) = LOWER(?) AND type = 'BAN' AND active = 1 ORDER BY timestamp DESC LIMIT 1";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, parsed != null ? parsed.toString() : idOrName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Punishment punishment = parsePunishment(rs);
                        String unbanReason = (reason == null || reason.isBlank()) ? "Manual unban" : reason;
                        if (deactivatePunishment(conn, punishment, staffUuid, staffName, unbanReason)) {
                            postRemovalSideEffects(punishment);
                            return true;
                        }
                        return false;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to unban '" + idOrName + "': " + e.getMessage());
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(idOrName);
            if (target.getUniqueId() != null && plugin.getConfig().getBoolean("punishment.sync-with-vanilla", true)) {
                ProfileBanList banList = (ProfileBanList) Bukkit.getBanList(BanList.Type.PROFILE);
                var profile = Bukkit.createPlayerProfile(target.getUniqueId());
                if (banList.isBanned(profile)) {
                    banList.pardon(profile);
                    plugin.getLogger().info("Removed vanilla ban for " + idOrName + " (no punishment record found)");
                    return true;
                }
            }
            return false;
        }, db);
    }
}
