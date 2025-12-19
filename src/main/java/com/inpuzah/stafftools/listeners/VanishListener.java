package com.inpuzah.stafftools.listeners;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.managers.VanishManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Ensures vanished players stay hidden and don't leak join/quit messages.
 */
public class VanishListener implements Listener {

    private final StaffToolsPlugin plugin;
    private final VanishManager vanish;

    public VanishListener(StaffToolsPlugin plugin) {
        this.plugin = plugin;
        this.vanish = plugin.getVanishManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    @SuppressWarnings("deprecation")
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (vanish == null || player == null) return;

        if (vanish.isVanished(player) && plugin.getConfig().getBoolean("vanish.fake-messages", true)) {
            // Prevent default quit message formatting from revealing the vanish staff member.
            event.setQuitMessage(null);
        }

        vanish.handleQuit(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (vanish == null) return;
        // Ensure visibility is correct for both directions when someone joins.
        vanish.updateVisibility(event.getPlayer());
    }
}
