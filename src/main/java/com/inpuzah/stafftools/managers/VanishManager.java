package com.inpuzah.stafftools.managers;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class VanishManager {

    @NotNull
    private final StaffToolsPlugin plugin;
    private final Map<UUID, Integer> vanishedPlayers = new HashMap<>(); // UUID -> level

    private org.bukkit.scheduler.BukkitTask actionBarTask;

    public VanishManager(StaffToolsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Reload scheduled tasks that depend on config. */
    public void reload() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
        startActionBarTask();

        // Re-apply visibility after reload in case priority settings changed.
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (UUID uuid : new HashSet<>(vanishedPlayers.keySet())) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline())
                    updateVisibility(p);
            }
        });
    }

    private void startActionBarTask() {
        if (!plugin.getConfig().getBoolean("vanish.action-bar-enabled", true))
            return;
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            String msg = plugin.getConfig().getString("vanish.action-bar-message", "&7You are &bvanished&7!");
            for (UUID uuid : vanishedPlayers.keySet()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline())
                    MessageUtil.sendActionBar(p, msg);
            }
        }, 20L, 20L);
    }

    public void setVanished(Player player, boolean vanished) {
        setVanished(player, vanished, 1);
    }

    public void setVanished(Player player, boolean vanished, int level) {
        if (vanished) {
            vanishedPlayers.put(player.getUniqueId(), level);
            applyVanish(player);
            MessageUtil.sendMessage(player, MessageUtil.getMessage("vanish.enabled"));
        } else {
            vanishedPlayers.remove(player.getUniqueId());
            removeVanish(player);
            MessageUtil.sendMessage(player, MessageUtil.getMessage("vanish.disabled"));
        }

        plugin.getAuditManager().logAction(
                player.getUniqueId(), player.getName(),
                vanished ? "VANISH_ENABLE" : "VANISH_DISABLE",
                null, null, "Vanish level: " + level);
    }

    @SuppressWarnings("nullness") // Bukkit.getOnlinePlayers() guarantees non-null players; plugin is @NotNull
                                  // field
    private void applyVanish(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!canSee(online, player)) {
                online.hidePlayer(plugin, player);
            }
        }
        if (plugin.getConfig().getBoolean("vanish.fake-messages", true)) {
            String leave = plugin.getConfig().getString("vanish.fake-leave-message",
                    "&e" + player.getName() + " left the game");
            Bukkit.broadcast(MessageUtil.component(leave));
        }
    }

    @SuppressWarnings("nullness") // Bukkit.getOnlinePlayers() guarantees non-null players
    private void removeVanish(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, player);
        }
        if (plugin.getConfig().getBoolean("vanish.fake-messages", true)) {
            String join = plugin.getConfig().getString("vanish.fake-join-message",
                    "&e" + player.getName() + " joined the game");
            Bukkit.broadcast(MessageUtil.component(join));
        }
    }

    public boolean isVanished(UUID uuid) {
        return vanishedPlayers.containsKey(uuid);
    }

    public boolean isVanished(Player p) {
        return isVanished(p.getUniqueId());
    }

    public int getVanishLevel(UUID uuid) {
        return vanishedPlayers.getOrDefault(uuid, 0);
    }

    public boolean canSee(Player viewer, Player target) {
        if (!isVanished(target))
            return true;
        if (!viewer.hasPermission("stafftools.vanish.see"))
            return false;

        if (plugin.getConfig().getBoolean("vanish.priority-levels.enabled", true)) {
            int t = getVanishLevel(target.getUniqueId());
            int v = isVanished(viewer) ? getVanishLevel(viewer.getUniqueId()) : 0;
            return v >= t || viewer.hasPermission("stafftools.vanish.priority");
        }
        return true;
    }

    @SuppressWarnings("nullness") // Bukkit.getOnlinePlayers() guarantees non-null players
    public void updateVisibility(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player))
                continue;

            if (canSee(other, player)) {
                other.showPlayer(plugin, player);
            } else {
                other.hidePlayer(plugin, player);
            }

            if (canSee(player, other)) {
                player.showPlayer(plugin, other);
            } else {
                player.hidePlayer(plugin, other);
            }
        }
    }

    /**
     * Cleanup hook for disconnects.
     * Ensures the vanish cache doesn't leak UUIDs and the player is shown to others
     * again.
     */
    @SuppressWarnings("nullness") // Bukkit.getOnlinePlayers() guarantees non-null players
    public void handleQuit(Player player) {
        if (!isVanished(player))
            return;

        vanishedPlayers.remove(player.getUniqueId());
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, player);
        }
    }

    public Set<UUID> getVanishedPlayers() {
        return new HashSet<>(vanishedPlayers.keySet());
    }
}
