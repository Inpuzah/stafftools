package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.UUID;

public class UnbanCommand implements CommandExecutor {

    private final StaffToolsPlugin plugin;

    public UnbanCommand(StaffToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("stafftools.punish.unban")) {
            MessageUtil.sendMessage(sender, MessageUtil.getMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.sendMessage(sender, "&cUsage: /" + label + " <playerName|uuid> [reason]");
            return true;
        }

        String targetArg = args[0];
        String reason = (args.length >= 2)
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "Manual unban";

        UUID staffUuid = (sender instanceof Player p)
                ? p.getUniqueId()
                : UUID.fromString("00000000-0000-0000-0000-000000000000");

        MessageUtil.sendMessage(sender, "&7Processing unban for &f" + targetArg + "&7...");

        plugin.getPunishmentManager().unbanByNameOrUuid(targetArg, staffUuid, sender.getName(), reason)
                .thenAccept(success ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (success) {
                                MessageUtil.sendMessage(sender, "&aSuccessfully unbanned &f" + targetArg + "&a.");
                            } else {
                                MessageUtil.sendMessage(sender, "&cNo active ban found for &f" + targetArg + "&c.");
                            }
                        })
                );

        return true;
    }
}
