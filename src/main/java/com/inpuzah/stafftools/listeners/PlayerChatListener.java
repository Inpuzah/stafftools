package com.inpuzah.stafftools.listeners;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Punishment;
import com.inpuzah.stafftools.utils.MessageUtil;
import com.inpuzah.stafftools.utils.TimeUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class PlayerChatListener implements Listener {

    private final StaffToolsPlugin plugin;

    public PlayerChatListener(StaffToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        // Handle GUI prompts first (write-in reason/duration).
        if (plugin.getChatPromptManager() != null && plugin.getChatPromptManager().handle(event)) {
            return;
        }

        Player player = event.getPlayer();

        // Check if player is muted
        if (plugin.getPunishmentManager().isPlayerMuted(player.getUniqueId())) {
            event.setCancelled(true);

            Punishment mute = plugin.getPunishmentManager().getActiveMute(player.getUniqueId());
            if (mute != null) {
                String message = "&cYou are muted for: &f" + mute.getReason();
                if (!mute.isPermanent()) {
                    message += "\n&cTime remaining: &f" + TimeUtil.formatDurationMillis(mute.getRemainingTime());
                } else {
                    message += "\n&cThis mute is permanent.";
                }
                MessageUtil.sendMessage(player, message);
            }
            return;
        }

        // Check if player is frozen
        if (plugin.getFreezeManager().isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
            MessageUtil.sendMessage(player, "&cYou cannot chat while frozen!");
        }
    }
}