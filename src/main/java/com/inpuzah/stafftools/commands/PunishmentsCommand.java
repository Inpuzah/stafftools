package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.gui.PunishmentGUI;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PunishmentsCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;
    public PunishmentsCommand(StaffToolsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player staff)) {
            MessageUtil.sendMessage(sender, "&cOnly players can use this command.");
            return true;
        }
        if (!staff.hasPermission("stafftools.punish.gui")) {
            MessageUtil.sendMessage(staff, MessageUtil.getMessage("no-permission"));
            return true;
        }
        if (args.length < 1) {
            MessageUtil.sendMessage(staff, "&cUsage: /" + label + " <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            // Optional: allow offline target but GUI expects a Player; fail gracefully
            OfflinePlayer off = Bukkit.getOfflinePlayer(args[0]);
            MessageUtil.sendMessage(staff, "&c" + (off.getName() == null ? "That player" : off.getName()) + " is not online.");
            return true;
        }

        new PunishmentGUI(plugin, staff, target).open();
        return true;
    }
}
