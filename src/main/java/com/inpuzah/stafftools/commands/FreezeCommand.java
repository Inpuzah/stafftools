package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.UUID;

public class FreezeCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;

    public FreezeCommand(StaffToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (!s.hasPermission("stafftools.freeze.use")) {
            MessageUtil.sendMessage(s, MessageUtil.getMessage("no-permission"));
            return true;
        }
        if (a.length < 1) {
            MessageUtil.sendMessage(s, "&cUsage: /" + l + " <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(a[0]);
        if (target == null) {
            MessageUtil.sendMessage(s, "&cPlayer must be online.");
            return true;
        }

        var mgr = plugin.getFreezeManager();
        try {
            boolean frozen = mgr.isFrozen(target);

            mgr.setFrozen(
                    target,
                    !frozen,
                    (s instanceof Player p) ? p.getUniqueId() : UUID.fromString("00000000-0000-0000-0000-000000000000"),
                    s.getName());

            MessageUtil.sendMessage(s, (!frozen) ? "&eFroze &f" + target.getName() : "&aUnfroze &f" + target.getName());
            return true;
        } catch (Exception e) {
            MessageUtil.sendMessage(s, "&cFailed: " + e.getMessage());
            return true;
        }
    }
}
