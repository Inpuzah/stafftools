package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Replacement for the /announce (/warn) Skript command
 * Broadcasts messages with BGSU branding and sends to Discord
 */
public class AnnounceCommand implements CommandExecutor {

    private final StaffToolsPlugin plugin;

    public AnnounceCommand(StaffToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("nullness") // Sound enum constants and Bukkit.getOnlinePlayers() are non-null
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("stafftools.announce")) {
            MessageUtil.sendMessage(sender,
                    "&6&lFalcon SMP &8| &6&lBG&7SU &8» &cYou don't have permission to broadcast messages.");
            return true;
        }

        if (args.length == 0) {
            MessageUtil.sendMessage(sender, "&c&l<!> &cUsage: &f/" + label + " <msg>");
            MessageUtil.sendMessage(sender, "&c&l<!> &cUsage: &f/" + label + " 1 <msg> &7(anonymous)");
            return true;
        }

        boolean anonymous = false;
        String message;

        // Check if first argument is "1" for anonymous mode
        if (args[0].equals("1")) {
            if (args.length < 2) {
                MessageUtil.sendMessage(sender, "&c&l<!> &cUsage: &f/" + label + " 1 <msg>");
                return true;
            }
            anonymous = true;
            message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        } else {
            message = String.join(" ", args);
        }

        // Format the broadcast message
        String broadcastMsg;
        if (anonymous) {
            // Anonymous broadcast (mode 1)
            broadcastMsg = "&6&lFalcon SMP &8| &6&lBG&7SU &8» &6" + message;
        } else {
            // Staff-attributed broadcast
            String staffName = sender instanceof Player ? sender.getName() : "Console";
            broadcastMsg = "&6&lFalcon SMP &8| &6&lBG&7SU &8» &b[&l" + staffName + "&r&b] &6" + message;
        }

        // Broadcast to all players
        Bukkit.broadcast(MessageUtil.component(broadcastMsg));

        // Play sound to all online players
        for (Player online : Bukkit.getOnlinePlayers()) {
            var loc = online.getLocation();
            if (loc != null) {
                online.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }

        // Send to Discord if enabled
        if (plugin.getDiscordManager().isEnabled()) {
            String discordMessage = anonymous ? message
                    : "[" + (sender instanceof Player ? sender.getName() : "Console") + "] " + message;
            plugin.getDiscordManager().sendStaffAnnouncement(
                    sender instanceof Player ? sender.getName() : null,
                    discordMessage,
                    label.equalsIgnoreCase("warn") // true if using /warn alias
            );
        }

        // Play sound to sender
        if (sender instanceof Player player) {
            var loc = player.getLocation();
            if (loc != null) {
                player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }

        return true;
    }
}