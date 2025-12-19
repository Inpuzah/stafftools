package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Punishment;
import com.inpuzah.stafftools.database.models.PunishmentType;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class WarnCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;

    public WarnCommand(StaffToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("stafftools.punish.warn")) {
            MessageUtil.sendMessage(sender, MessageUtil.getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(sender, "&cUsage: /warn <player> <reason>");
            return true;
        }

        String targetArg = args[0];
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

        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        Punishment punishment = new Punishment(
                off.getUniqueId(),
                off.getName() != null ? off.getName() : targetArg,
                sender instanceof Player p ? p.getUniqueId() : UUID.fromString("00000000-0000-0000-0000-000000000000"),
                sender.getName(),
                PunishmentType.WARN,
                reason,
                0);

        punishment.setServerName(Bukkit.getServer().getName());
        if (online != null) {
            var address = online.getAddress();
            if (address != null && address.getAddress() != null) {
                punishment.setIpAddress(address.getAddress().getHostAddress());
            }
        }

        plugin.getPunishmentManager().issuePunishment(punishment).thenAccept(issued -> {
            if (issued != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String message = MessageUtil.getMessage("punishment.staff-warned")
                            .replace("{player}", punishment.getPlayerName())
                            .replace("{reason}", reason);
                    MessageUtil.sendMessage(sender, message);
                });
            }
        });

        return true;
    }
}