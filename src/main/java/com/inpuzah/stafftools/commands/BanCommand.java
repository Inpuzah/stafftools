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

import java.util.Arrays;
import java.util.UUID;

public class BanCommand implements CommandExecutor {
    private static final UUID CONSOLE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private final StaffToolsPlugin plugin;

    public BanCommand(StaffToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("stafftools.punish.ban")) {
            MessageUtil.sendMessage(sender, MessageUtil.getMessage("no-permission"));
            return true;
        }

        if (args.length < 3) {
            MessageUtil.sendMessage(sender, "&cUsage: /ban <player> <duration|perm> <reason>");
            MessageUtil.sendMessage(sender, "&7Example: /ban Player123 7d Griefing");
            return true;
        }

        // Resolve online/offline target safely
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

        long duration = com.inpuzah.stafftools.utils.TimeUtil.parseDuration(args[1]);
        if (duration < 0) {
            MessageUtil.sendMessage(sender, "&cInvalid duration. Use like &f30m&c, &f1h&c, &f7d&c, or &fperm&c.");
            return true;
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        UUID staffUUID = (sender instanceof Player p) ? p.getUniqueId() : CONSOLE_UUID;
        String staffName = sender.getName();

        Punishment punishment = new Punishment(
                off.getUniqueId(),
                off.getName() != null ? off.getName() : targetArg,
                staffUUID,
                staffName,
                PunishmentType.BAN,
                reason,
                duration);

        punishment.setServerName(Bukkit.getServer().getName());
        if (online != null) {
            var address = online.getAddress();
            if (address != null && address.getAddress() != null) {
                punishment.setIpAddress(address.getAddress().getHostAddress());
            }
        }

        plugin.getPunishmentManager().issuePunishment(punishment)
                .thenAccept(saved -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (saved == null) {
                        MessageUtil.sendMessage(sender, MessageUtil.getMessage("punishment.already-punished"));
                        return;
                    }

                    String msg = MessageUtil.getMessage("punishment.staff-banned")
                            .replace("{player}", punishment.getPlayerName())
                            .replace("{duration}",
                                    duration == 0 ? "Permanent"
                                            : com.inpuzah.stafftools.utils.TimeUtil.formatDuration(duration))
                            .replace("{reason}", reason);
                    MessageUtil.sendMessage(sender, msg);
                }));

        return true;
    }
}
