package com.inpuzah.stafftools.listeners;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Punishment;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final StaffToolsPlugin plugin;

    public PlayerJoinListener(StaffToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    @SuppressWarnings("deprecation")
    public void onPlayerLogin(org.bukkit.event.player.PlayerLoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Login decisions MUST be synchronous. Use the in-memory cache.
        Punishment ban = plugin.getPunishmentManager().getActiveBan(uuid);
        if (ban == null)
            return;

        String banScreen = plugin.getPunishmentManager().buildBanScreenMessage(ban);
        event.disallow(org.bukkit.event.player.PlayerLoginEvent.Result.KICK_BANNED,
                MessageUtil.colorize(banScreen));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Handle buildban restoration/application
        plugin.getBuildBanManager().handlePlayerJoin(player);

        // Check punishment history and notify staff
        if (plugin.getConfig().getBoolean("notifications.join-notifications", true)) {
            int threshold = plugin.getConfig().getInt("notifications.join-notification-threshold", 3);

            plugin.getPunishmentManager().getPlayerHistory(player.getUniqueId()).thenAccept(history -> {
                if (history.size() >= threshold) {
                    String format = plugin.getConfig().getString("notifications.format.join")
                            .replace("{player}", player.getName())
                            .replace("{count}", String.valueOf(history.size()));

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (Player staff : Bukkit.getOnlinePlayers()) {
                            if (staff != null && staff.hasPermission("stafftools.staff.notify")) {
                                MessageUtil.sendMessage(staff, format);
                            }
                        }
                    });
                }
            });
        }

        // Restore vanish state for staff
        if (plugin.getVanishManager() != null && player.hasPermission("stafftools.vanish.use")) {
            // TODO: optional persistence if you want vanish/staffmode to survive
            // reconnects.
        }
    }
}