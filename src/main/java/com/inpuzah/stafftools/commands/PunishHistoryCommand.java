package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.gui.PunishmentHistoryGUI;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PunishHistoryCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;
    public PunishHistoryCommand(StaffToolsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player staff)) {
            MessageUtil.sendMessage(sender, "&cOnly players can use this command.");
            return true;
        }
        if (!staff.hasPermission("stafftools.punish.history")) {
            MessageUtil.sendMessage(staff, MessageUtil.getMessage("no-permission"));
            return true;
        }
        if (args.length < 1) {
            MessageUtil.sendMessage(staff, "&cUsage: /" + label + " <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(args[0]);
            if (off == null || off.getName() == null) {
                MessageUtil.sendMessage(staff, "&cUnknown player.");
                return true;
            }
            MessageUtil.sendMessage(staff, "&c" + off.getName() + " is not online. History GUI requires the player to be online.");
            return true;
        }

        new PunishmentHistoryGUI(plugin, staff, target).open();
        return true;
    }
}
