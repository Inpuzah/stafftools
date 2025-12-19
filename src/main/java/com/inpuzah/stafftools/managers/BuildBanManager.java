package com.inpuzah.stafftools.managers;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Punishment;
import com.inpuzah.stafftools.database.models.PunishmentType;
import com.inpuzah.stafftools.utils.MessageUtil;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class BuildBanManager {

    private final StaffToolsPlugin plugin;
    private final Map<UUID, Punishment> activeBuildBans;
    private final Map<UUID, String> originalGroups; // Store original group for restoration

    public BuildBanManager(StaffToolsPlugin plugin) {
        this.plugin = plugin;
        this.activeBuildBans = new HashMap<>();
        this.originalGroups = new HashMap<>();

        loadActiveBuildBans();
    }

    private void loadActiveBuildBans() {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = "SELECT * FROM punishments WHERE type = 'BUILDBAN' AND active = 1";
                try (PreparedStatement stmt = conn.prepareStatement(query);
                        ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        Punishment punishment = parsePunishment(rs);
                        if (!punishment.isExpired()) {
                            activeBuildBans.put(punishment.getPlayerUuid(), punishment);

                            // Apply buildban to online players
                            Player player = Bukkit.getPlayer(punishment.getPlayerUuid());
                            if (player != null && player.isOnline()) {
                                Bukkit.getScheduler().runTask(plugin, () -> applyBuildBan(player));
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load active buildbans: " + e.getMessage());
            }
        });
    }

    public void issueBuildBan(Punishment punishment) {
        activeBuildBans.put(punishment.getPlayerUuid(), punishment);

        Player player = Bukkit.getPlayer(punishment.getPlayerUuid());
        if (player != null && player.isOnline()) {
            applyBuildBan(player);

            String message = MessageUtil.colorize(
                    "&c&lYou have been BuildBanned!\n" +
                            "&7Reason: &f" + punishment.getReason() + "\n" +
                            "&7Duration: &f"
                            + (punishment.isPermanent() ? "Permanent"
                                    : com.inpuzah.stafftools.utils.TimeUtil.formatDuration(punishment.getDuration()))
                            + "\n" +
                            "&7You can still explore and chat, but cannot build.");
            player.sendMessage(message);
        }
    }

    private void applyBuildBan(Player player) {
        UUID uuid = player.getUniqueId();

        // Get LuckPerms user
        User user = plugin.getLuckPerms().getUserManager().getUser(uuid);
        if (user == null) {
            plugin.getLogger().warning("Could not find LuckPerms user for " + player.getName());
            return;
        }

        if (plugin.getConfig().getBoolean("buildban.restore-original-group", true)) {
            // Store original primary group
            String originalGroup = user.getPrimaryGroup();
            originalGroups.put(uuid, originalGroup);

            // Save to database for persistence
            saveOriginalGroup(uuid, originalGroup);

            // Set to buildbanned group
            String buildBannedGroup = plugin.getConfig().getString("buildban.demoted-group", "buildbanned");
            var data = user.data();
            if (data != null) {
                data.clear(node -> node.getKey().startsWith("group."));
                data.add(Node.builder("group." + buildBannedGroup).build());
            }

            // Save changes
            plugin.getLuckPerms().getUserManager().saveUser(user);
        } else {
            // Remove specific permissions
            List<String> permissions = plugin.getConfig().getStringList("buildban.remove-permissions");
            var data = user.data();
            if (data != null) {
                for (String perm : permissions) {
                    @SuppressWarnings("nullness")
                    var ignored = data.add(Node.builder(perm).value(false).build());
                }
            }

            plugin.getLuckPerms().getUserManager().saveUser(user);
        }

        plugin.getLogger().info("Applied buildban to " + player.getName());
    }

    public void removeBuildBan(UUID playerUuid) {
        Punishment buildban = activeBuildBans.remove(playerUuid);
        if (buildban == null) {
            return;
        }

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            restoreBuildPermissions(player);
            MessageUtil.sendMessage(player, "&aYour buildban has been removed! You can now build again.");
        } else {
            // Player offline - will be restored on next login
            // Mark for restoration in database
            markForRestoration(playerUuid);
        }
    }

    private void restoreBuildPermissions(Player player) {
        UUID uuid = player.getUniqueId();

        User user = plugin.getLuckPerms().getUserManager().getUser(uuid);
        if (user == null) {
            plugin.getLogger().warning("Could not find LuckPerms user for " + player.getName());
            return;
        }

        if (plugin.getConfig().getBoolean("buildban.restore-original-group", true)) {
            // Restore original group
            String originalGroup = originalGroups.getOrDefault(uuid, getStoredOriginalGroup(uuid));

            if (originalGroup != null) {
                var data = user.data();
                if (data != null) {
                    data.clear(node -> node.getKey().startsWith("group."));
                    data.add(Node.builder("group." + originalGroup).build());
                }

                plugin.getLuckPerms().getUserManager().saveUser(user);
                originalGroups.remove(uuid);
                clearStoredOriginalGroup(uuid);
            }
        } else {
            // Re-add permissions
            List<String> permissions = plugin.getConfig().getStringList("buildban.remove-permissions");
            var data = user.data();
            if (data != null) {
                for (String perm : permissions) {
                    @SuppressWarnings("nullness")
                    var ignored = data.remove(Node.builder(perm).value(false).build());
                }
            }

            plugin.getLuckPerms().getUserManager().saveUser(user);
        }

        plugin.getLogger().info("Restored build permissions for " + player.getName());
    }

    public boolean isBuildBanned(UUID playerUuid) {
        Punishment buildban = activeBuildBans.get(playerUuid);
        if (buildban != null && buildban.isExpired()) {
            activeBuildBans.remove(playerUuid);
            removeBuildBan(playerUuid);
            return false;
        }
        return buildban != null;
    }

    public Punishment getActiveBuildBan(UUID playerUuid) {
        return activeBuildBans.get(playerUuid);
    }

    public void handlePlayerJoin(Player player) {
        // Check if player needs buildban applied or restored
        // Apply immediately from cache (fast)
        if (isBuildBanned(player.getUniqueId())) {
            applyBuildBan(player);
        } else {
            // Check for restoration asynchronously to avoid blocking main thread
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (needsRestoration(player.getUniqueId())) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        restoreBuildPermissions(player);
                        clearRestorationFlag(player.getUniqueId());
                    });
                }
            });
        }
    }

    private void saveOriginalGroup(UUID playerUuid, String group) {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = "INSERT OR REPLACE INTO buildban_groups (player_uuid, original_group) VALUES (?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, group);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save original group: " + e.getMessage());
            }
        });
    }

    private String getStoredOriginalGroup(UUID playerUuid) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "SELECT original_group FROM buildban_groups WHERE player_uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, playerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("original_group");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get stored original group: " + e.getMessage());
        }
        return null;
    }

    private void clearStoredOriginalGroup(UUID playerUuid) {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = "DELETE FROM buildban_groups WHERE player_uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to clear stored original group: " + e.getMessage());
            }
        });
    }

    private void markForRestoration(UUID playerUuid) {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = "UPDATE buildban_groups SET needs_restoration = 1 WHERE player_uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to mark for restoration: " + e.getMessage());
            }
        });
    }

    private boolean needsRestoration(UUID playerUuid) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "SELECT needs_restoration FROM buildban_groups WHERE player_uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, playerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean("needs_restoration");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check restoration flag: " + e.getMessage());
        }
        return false;
    }

    private void clearRestorationFlag(UUID playerUuid) {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = "UPDATE buildban_groups SET needs_restoration = 0 WHERE player_uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to clear restoration flag: " + e.getMessage());
            }
        });
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
                rs.getLong("expires_at") == 0 ? null : rs.getLong("expires_at"),
                rs.getBoolean("active"),
                rs.getString("removed_by") != null ? UUID.fromString(rs.getString("removed_by")) : null,
                rs.getLong("removed_at") == 0 ? null : rs.getLong("removed_at"),
                rs.getString("removed_reason"),
                rs.getString("server_name"),
                rs.getString("ip_address"));
    }
}