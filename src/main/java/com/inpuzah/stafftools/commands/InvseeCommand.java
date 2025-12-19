package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class InvseeCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (!(s instanceof Player staff)) { MessageUtil.sendMessage(s,"&cOnly in-game."); return true; }
        if (!staff.hasPermission("stafftools.invsee")) { MessageUtil.sendMessage(staff, MessageUtil.getMessage("no-permission")); return true; }
        if (a.length<1){ MessageUtil.sendMessage(staff,"&cUsage: /"+l+" <player>"); return true; }
        Player target = Bukkit.getPlayerExact(a[0]);
        if (target==null){ MessageUtil.sendMessage(staff,"&cPlayer must be online."); return true; }
        staff.openInventory(target.getInventory());
        MessageUtil.sendMessage(staff,"&aOpened &f"+target.getName()+"&a's inventory.");
        return true;
    }
}
