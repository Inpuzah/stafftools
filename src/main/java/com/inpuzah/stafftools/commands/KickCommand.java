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

public class KickCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;

    public KickCommand(StaffToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("stafftools.punish.kick")) {
            MessageUtil.sendMessage(sender, MessageUtil.getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(sender, "&cUsage: /kick <player> <reason>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            MessageUtil.sendMessage(sender, MessageUtil.getMessage("player-not-online"));
            return true;
        }

        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        Punishment punishment = new Punishment(
                target.getUniqueId(),
                target.getName(),
                sender instanceof Player p ? p.getUniqueId() : UUID.fromString("00000000-0000-0000-0000-000000000000"),
                sender.getName(),
                PunishmentType.KICK,
                reason,
                0);

        punishment.setServerName(Bukkit.getServer().getName());
        var address = target.getAddress();
        if (address != null && address.getAddress() != null) {
            punishment.setIpAddress(address.getAddress().getHostAddress());
        }

        plugin.getPunishmentManager().issuePunishment(punishment).thenAccept(issued -> {
            if (issued != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String message = MessageUtil.getMessage("punishment.staff-kicked")
                            .replace("{player}", target.getName())
                            .replace("{reason}", reason);
                    MessageUtil.sendMessage(sender, message);
                });
            }
        });

        return true;
    }
}