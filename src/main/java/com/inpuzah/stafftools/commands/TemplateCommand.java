package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TemplateCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;
    public TemplateCommand(StaffToolsPlugin plugin){ this.plugin=plugin; }

    @Override public boolean onCommand(@NotNull CommandSender s,@NotNull Command c,@NotNull String l,@NotNull String[] a){
        if (!s.hasPermission("stafftools.template.use")) { MessageUtil.sendMessage(s, MessageUtil.getMessage("no-permission")); return true; }
        if (plugin.getConfig().getConfigurationSection("templates") == null) {
            MessageUtil.sendMessage(s, "&cTemplates are not configured. Set templates.* in config.yml or ignore this command.");
            return true;
        }
        if (a.length<2){ MessageUtil.sendMessage(s,"&cUsage: /"+l+" <template-key> <player>"); return true; }
        String key=a[0];
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target==null){ MessageUtil.sendMessage(s,"&cPlayer must be online."); return true; }
        String msg = plugin.getConfig().getString("templates."+key);
        if (msg==null){ MessageUtil.sendMessage(s,"&cUnknown template '"+key+"'."); return true; }
        target.sendMessage(MessageUtil.colorize(msg.replace("{player}", target.getName()).replace("{staff}", s.getName())));
        MessageUtil.sendMessage(s,"&aSent template '"+key+"' to &f"+target.getName());
        return true;
    }
}
