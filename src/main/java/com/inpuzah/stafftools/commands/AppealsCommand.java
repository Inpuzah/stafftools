package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Appeal;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

public class AppealsCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;
    public AppealsCommand(StaffToolsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("stafftools.appeal.list")) {
            MessageUtil.sendMessage(sender, MessageUtil.getMessage("no-permission")); return true;
        }

        Object mgr = plugin.getAppealManager();
        Object res = null;

        try {
            // Try listOpenAppeals() then getOpenAppeals()
            for (String name : new String[]{"listOpenAppeals","getOpenAppeals","listAppeals","getAppeals"}) {
                try {
                    Method m = mgr.getClass().getMethod(name);
                    res = m.invoke(mgr);
                    if (res != null) break;
                } catch (NoSuchMethodException ignore) {}
            }

            Collection<?> appeals = null;
            if (res instanceof Collection<?> c) appeals = c;
            if (res instanceof List<?> l) appeals = l;

            if (appeals == null || appeals.isEmpty()) {
                MessageUtil.sendMessage(sender, "&7No open appeals.");
                return true;
            }

            MessageUtil.sendMessage(sender, "&bOpen Appeals (" + appeals.size() + "):");
            int shown = 0;
            for (Object o : appeals) {
                if (!(o instanceof Appeal a)) {
                    MessageUtil.sendMessage(sender, "&7- &f" + String.valueOf(o));
                } else {
                    // Basic line without touching enums
                    MessageUtil.sendMessage(sender, "&7- &f#" + a.getId() + " &8— &f" + a.getPlayerName() + " &8/ &7Punishment: " + a.getPunishmentId());
                }
                if (++shown >= 20) { MessageUtil.sendMessage(sender, "&8…and more"); break; }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("AppealsCommand error: " + e.getMessage());
            MessageUtil.sendMessage(sender, "&cFailed to list appeals. See console.");
        }
        return true;
    }
}
