package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Punishment;
import com.inpuzah.stafftools.database.models.PunishmentType;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class UnBuildBanCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;
    public UnBuildBanCommand(StaffToolsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("stafftools.punish.unbuildban")) {
            MessageUtil.sendMessage(sender, MessageUtil.getMessage("no-permission"));
            return true;
        }
        if (args.length < 1) {
            MessageUtil.sendMessage(sender, "&cUsage: /" + label + " <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

        // Find active buildban in DB, then remove it through PunishmentManager to ensure full cleanup.
        plugin.getPunishmentManager().getPlayerHistory(target.getUniqueId()).thenAccept(history -> {
            Punishment active = null;
            for (Punishment p : history) {
                if (p.getType() == PunishmentType.BUILDBAN && p.isActive()) { active = p; break; }
            }

            if (active == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        MessageUtil.sendMessage(sender, "&cThat player is not currently BuildBanned."));
                return;
            }

            UUID staffUUID = (sender instanceof Player pl) ? pl.getUniqueId()
                    : UUID.fromString("00000000-0000-0000-0000-000000000000");

            plugin.getPunishmentManager().removePunishment(
                    active.getId(),
                    staffUUID,
                    sender.getName(),
                    "BuildBan removed"
            ).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    MessageUtil.sendMessage(sender, "&aRemoved BuildBan from &f" +
                            (target.getName() != null ? target.getName() : args[0]));
                } else {
                    MessageUtil.sendMessage(sender, "&cFailed to remove BuildBan.");
                }
            }));
        });

        return true;
    }
}
