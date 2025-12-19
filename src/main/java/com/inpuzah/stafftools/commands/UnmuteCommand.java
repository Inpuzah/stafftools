package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Punishment;

import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class UnmuteCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;

    public UnmuteCommand(StaffToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("stafftools.punish.unmute")) {
            MessageUtil.sendMessage(sender, MessageUtil.getMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.sendMessage(sender, "&cUsage: /unmute <player|uuid> [reason]");
            return true;
        }

        String targetArg = args[0];
        String reason = (args.length >= 2)
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : "Unmuted by staff";

        Player online = Bukkit.getPlayerExact(targetArg);
        org.bukkit.OfflinePlayer off;
        UUID parsedUuid = null;
        try {
            parsedUuid = UUID.fromString(targetArg);
        } catch (IllegalArgumentException ignored) {
        }

        if (online != null) {
            off = online;
        } else if (parsedUuid != null) {
            off = Bukkit.getOfflinePlayer(parsedUuid);
        } else {
            off = Bukkit.getOfflinePlayer(targetArg);
            if (!off.hasPlayedBefore()) {
                MessageUtil.sendMessage(sender, MessageUtil.getMessage("player-not-found"));
                return true;
            }
        }

        Punishment activeMute = plugin.getPunishmentManager().getActiveMute(off.getUniqueId());
        if (activeMute == null) {
            MessageUtil.sendMessage(sender, MessageUtil.getMessage("punishment.not-punished"));
            return true;
        }

        UUID staffUUID = (sender instanceof Player p) ? p.getUniqueId()
                : UUID.fromString("00000000-0000-0000-0000-000000000000");

        plugin.getPunishmentManager().removePunishment(
                activeMute.getId(),
                staffUUID,
                sender.getName(),
                reason).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        MessageUtil.sendMessage(sender,
                                "&aUnmuted &f" + (off.getName() != null ? off.getName() : targetArg) + "&a.");
                        if (online != null) {
                            MessageUtil.sendMessage(online, MessageUtil.getMessage("punishment.unmuted"));
                        }
                    } else {
                        MessageUtil.sendMessage(sender, "&cFailed to unmute.");
                    }
                }));

        return true;
    }
}