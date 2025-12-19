package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public class VanishCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;
    public VanishCommand(StaffToolsPlugin plugin){ this.plugin=plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (!(s instanceof Player p)) { MessageUtil.sendMessage(s,"&cOnly in-game."); return true; }
        if (!p.hasPermission("stafftools.vanish.toggle")) { MessageUtil.sendMessage(p, MessageUtil.getMessage("no-permission")); return true; }

        var mgr = plugin.getVanishManager();
        try {
            Method isVanished = mgr.getClass().getMethod("isVanished", Player.class);
            boolean v = (boolean) isVanished.invoke(mgr, p);

            // Try setVanished(Player, boolean)
            try {
                Method setV = mgr.getClass().getMethod("setVanished", Player.class, boolean.class);
                setV.invoke(mgr, p, !v);
            } catch (NoSuchMethodException e) {
                // Fallback to enable/disable
                Method m = mgr.getClass().getMethod(v ? "disableVanish" : "enableVanish", Player.class);
                m.invoke(mgr, p);
            }

            // Messages are handled inside VanishManager
            return true;
        } catch (Exception e) {
            MessageUtil.sendMessage(p, "&cFailed to toggle vanish: " + e.getMessage());
            return true;
        }
    }
}
