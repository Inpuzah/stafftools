package com.inpuzah.stafftools.managers;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Punishment;
import com.inpuzah.stafftools.database.models.PunishmentType;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FreezeManager {

    private final StaffToolsPlugin plugin;
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();

    public FreezeManager(StaffToolsPlugin plugin) {
        this.plugin = plugin;
        startFreezeTask();
    }

    private void startFreezeTask() {
        // Every 10s, ping frozen players; iterate over snapshot to avoid CME
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : new ArrayList<>(frozenPlayers)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;
                sendFreezeMessage(p);
            }
        }, 200L, 200L);
    }

    // --- Overloads accepting Player (used by command) ---
    public void freeze(Player player, UUID staffUuid, String staffName) {
        if (player == null) return;
        freezePlayer(player.getUniqueId(), staffUuid, staffName);
    }

    public void unfreeze(Player player, UUID staffUuid, String staffName) {
        if (player == null) return;
        unfreezePlayer(player.getUniqueId(), staffUuid, staffName);
    }

    public void setFrozen(Player player, boolean frozen, UUID staffUuid, String staffName) {
        if (player == null) return;
        if (frozen) {
            freezePlayer(player.getUniqueId(), staffUuid, staffName);
        } else {
            unfreezePlayer(player.getUniqueId(), staffUuid, staffName);
        }
    }

    // --- UUID-based core API (used by listeners/commands) ---
    public void freezePlayer(UUID playerUuid, UUID staffUuid, String staffName) {
        frozenPlayers.add(playerUuid);
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) sendFreezeMessage(player);

        plugin.getAuditManager().logAction(
                staffUuid, staffName, "FREEZE", playerUuid,
                player != null ? player.getName() : "Unknown", "Frozen by staff");
    }

    public void unfreezePlayer(UUID playerUuid, UUID staffUuid, String staffName) {
        frozenPlayers.remove(playerUuid);
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline())
            MessageUtil.sendMessage(player, "&a&lYou have been unfrozen!");

        plugin.getAuditManager().logAction(
                staffUuid, staffName, "UNFREEZE", playerUuid,
                player != null ? player.getName() : "Unknown", "Unfrozen by staff");
    }

    public boolean isFrozen(UUID playerUuid) { return frozenPlayers.contains(playerUuid); }
    public boolean isFrozen(Player player) { return player != null && isFrozen(player.getUniqueId()); }

    public void handleLogout(UUID playerUuid) {
        if (!isFrozen(playerUuid)) return;
        frozenPlayers.remove(playerUuid);

        if (plugin.getConfig().getBoolean("freeze.ban-on-logout", true)) {
            Player p = Bukkit.getPlayer(playerUuid);
            String name = (p != null) ? p.getName() : "Unknown";
            long minutes = plugin.getConfig().getLong("freeze.ban-duration", 0L);
            String reason = plugin.getConfig().getString("freeze.ban-reason", "Logged out while frozen");

            Punishment punishment = new Punishment(
                    playerUuid, name,
                    UUID.fromString("00000000-0000-0000-0000-000000000000"),
                    "CONSOLE", PunishmentType.BAN, reason, minutes
            );
            plugin.getPunishmentManager().issuePunishment(punishment);
        }
    }

    private void sendFreezeMessage(Player player) {
        player.sendMessage("");
        for (String line : plugin.getConfig().getStringList("freeze.freeze-message")) {
            player.sendMessage(MessageUtil.colorize(
                    line.replace("{discord-url}", plugin.getConfig().getString("freeze.discord-url", "discord.gg/yourserver"))
            ));
        }
        player.sendMessage("");
    }

    public Set<UUID> getFrozenPlayers() { return new HashSet<>(frozenPlayers); }
}
