package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class StaffToolsCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;

    public StaffToolsCommand(StaffToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("stafftools.admin")) {
            MessageUtil.sendMessage(sender, MessageUtil.getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(MessageUtil.colorize("&c&lStaffTools &7v" + plugin.getPluginMeta().getVersion()));
            sender.sendMessage(MessageUtil.colorize("&7/stafftools reload &f- Reload config"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadStaffTools();
            MessageUtil.sendMessage(sender, "&aStaffTools reloaded! (&7Config + runtime managers& a)");
            MessageUtil.sendMessage(sender, "&7Note: some settings still require a full server restart.");
            return true;
        }

        return true;
    }
}