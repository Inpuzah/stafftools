package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Punishment;
import com.inpuzah.stafftools.database.models.PunishmentType;
import com.inpuzah.stafftools.utils.MessageUtil;
import com.inpuzah.stafftools.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.UUID;

public class BuildBanCommand implements CommandExecutor {

    private final StaffToolsPlugin plugin;

    public BuildBanCommand(StaffToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {

        if (!sender.hasPermission("stafftools.punish.buildban")) {
            MessageUtil.sendMessage(sender, MessageUtil.getMessage("no-permission"));
            return true;
        }

        if (args.length < 3) {
            MessageUtil.sendMessage(sender, "&cUsage: /" + label + " <player> <duration|perm> <reason>");
            MessageUtil.sendMessage(sender, "&7Example: /" + label + " Player123 7d Griefing");
            return true;
        }

        final String targetArg = args[0];
        final Player online = Bukkit.getPlayerExact(targetArg);
        final OfflinePlayer off;

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

        final long minutes = TimeUtil.parseDuration(args[1]);
        if (minutes < 0) {
            MessageUtil.sendMessage(sender, "&cInvalid duration. Use like &f30m&c, &f1h&c, &f7d&c, or &fperm&c.");
            return true;
        }

        final String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        final UUID staffUuid = (sender instanceof Player p) ? p.getUniqueId()
                : UUID.fromString("00000000-0000-0000-0000-000000000000");
        final String staffName = sender.getName();

        final Punishment punishment = new Punishment(
                off.getUniqueId(),
                off.getName() != null ? off.getName() : targetArg,
                staffUuid,
                staffName,
                PunishmentType.BUILDBAN,
                reason,
                minutes);

        punishment.setServerName(Bukkit.getServer().getName());
        if (online != null) {
            var address = online.getAddress();
            if (address != null && address.getAddress() != null) {
                punishment.setIpAddress(address.getAddress().getHostAddress());
            }
        }

        plugin.getPunishmentManager().issuePunishment(punishment).thenAccept(issued -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (issued == null) {
                    MessageUtil.sendMessage(sender, MessageUtil.getMessage("punishment.already-punished"));
                    return;
                }

                String durTxt = (minutes == 0L) ? "Permanent" : TimeUtil.formatDuration(minutes);
                MessageUtil.sendMessage(sender, "&aBuildBanned &f" + punishment.getPlayerName()
                        + "&a for &f" + durTxt + "&a: &f" + reason);
            });
        });

        return true;
    }
}
