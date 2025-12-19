package com.inpuzah.stafftools.listeners;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.managers.FreezeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.entity.Player;

public class FreezeListener implements Listener {

    private final FreezeManager freeze;

    public FreezeListener(StaffToolsPlugin plugin) {
        this.freeze = plugin.getFreezeManager();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!freeze.isFrozen(p)) return;
        if (e.getTo() != null && e.getFrom().distanceSquared(e.getTo()) > 0) {
            e.setTo(e.getFrom()); // hard stop
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!freeze.isFrozen(p)) return;
        String cmd = e.getMessage().toLowerCase();
        // Allow only whitelisted commands while frozen
        if (cmd.startsWith("/msg") || cmd.startsWith("/r") ||
                cmd.startsWith("/helpop") || cmd.startsWith("/appeal")) {
            return;
        }
        e.setCancelled(true);
        p.sendMessage("§cYou are frozen. Cooperate with staff.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        freeze.handleLogout(e.getPlayer().getUniqueId());
    }
}
