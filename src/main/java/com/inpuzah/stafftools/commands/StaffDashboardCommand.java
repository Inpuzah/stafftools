package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.gui.PunishmentHistoryGUI;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class StaffDashboardCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;
    public StaffDashboardCommand(StaffToolsPlugin plugin){ this.plugin=plugin; }

    @Override public boolean onCommand(@NotNull CommandSender s,@NotNull Command c,@NotNull String l,@NotNull String[] a){
        if (!(s instanceof Player staff)) { MessageUtil.sendMessage(s,"&cOnly in-game."); return true; }
        if (a.length<1){ MessageUtil.sendMessage(staff,"&cUsage: /"+l+" <online-player>"); return true; }
        Player target = Bukkit.getPlayerExact(a[0]);
        if (target==null){ MessageUtil.sendMessage(staff,"&cPlayer must be online."); return true; }
        new PunishmentHistoryGUI(plugin, staff, target).open();
        return true;
    }
}
