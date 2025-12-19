package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.gui.PunishmentGUI;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PunishCommand implements CommandExecutor {

    private final StaffToolsPlugin plugin;

    public PunishCommand(StaffToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, MessageUtil.getMessage("must-be-player"));
            return true;
        }

        if (!player.hasPermission("stafftools.punish.use")) {
            MessageUtil.sendMessage(player, MessageUtil.getMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.sendMessage(player, "&cUsage: /punish <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            MessageUtil.sendMessage(player, MessageUtil.getMessage("player-not-found"));
            return true;
        }

        // Open punishment GUI
        PunishmentGUI gui = new PunishmentGUI(plugin, player, target);
        gui.open();

        return true;
    }
}