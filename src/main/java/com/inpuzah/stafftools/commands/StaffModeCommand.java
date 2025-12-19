package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public class StaffModeCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;
    public StaffModeCommand(StaffToolsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (!(s instanceof Player p)) { MessageUtil.sendMessage(s,"&cOnly in-game."); return true; }
        if (!p.hasPermission("stafftools.staffmode.toggle")) { MessageUtil.sendMessage(p, MessageUtil.getMessage("no-permission")); return true; }

        var mgr = plugin.getStaffModeManager();

        try {
            // Prefer: isInStaffMode / enable / disable
            Method isIn = mgr.getClass().getMethod("isInStaffMode", Player.class);
            boolean in = (boolean) isIn.invoke(mgr, p);
            if (in) {
                Method disable = mgr.getClass().getMethod("disableStaffMode", Player.class);
                disable.invoke(mgr, p);
                MessageUtil.sendMessage(p, "&7StaffMode disabled.");
            } else {
                Method enable = mgr.getClass().getMethod("enableStaffMode", Player.class);
                enable.invoke(mgr, p);
                MessageUtil.sendMessage(p, "&aStaffMode enabled.");
            }
            return true;
        } catch (NoSuchMethodException nsme) {
            // Fallback to a simple toggle(Player) if it exists
            try {
                Method toggle = mgr.getClass().getMethod("toggle", Player.class);
                boolean enabled = (boolean) toggle.invoke(mgr, p);
                MessageUtil.sendMessage(p, enabled ? "&aStaffMode enabled." : "&7StaffMode disabled.");
                return true;
            } catch (Exception ignore) {
                MessageUtil.sendMessage(p, "&cStaffMode manager does not support toggling.");
                return true;
            }
        } catch (Exception e) {
            MessageUtil.sendMessage(p, "&cFailed to toggle StaffMode: " + e.getMessage());
            return true;
        }
    }
}
