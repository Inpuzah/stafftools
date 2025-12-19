package com.inpuzah.stafftools.listeners;

import com.inpuzah.stafftools.StaffToolsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {
    public PlayerQuitListener(StaffToolsPlugin plugin) {}
    @EventHandler public void onQuit(PlayerQuitEvent e) { /* optional cleanup via managers */ }
}
