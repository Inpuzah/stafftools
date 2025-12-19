package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Report;
import com.inpuzah.stafftools.database.models.ReportStatus;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

public class ReportsCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;
    public ReportsCommand(StaffToolsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("stafftools.report.list")) {
            MessageUtil.sendMessage(sender, MessageUtil.getMessage("no-permission")); return true;
        }

        try {
            var future = plugin.getReportManager().getReports(ReportStatus.OPEN);
            var reports = future.join();

            if (reports == null || reports.isEmpty()) {
                MessageUtil.sendMessage(sender, "&7No open reports.");
                return true;
            }

            MessageUtil.sendMessage(sender, "&bOpen Reports (" + reports.size() + "):");
            int shown = 0;
            for (Report r : reports) {
                String who = r.getReportedName() != null ? r.getReportedName() : "unknown";
                String why = r.getReason() != null ? r.getReason() : "no reason";
                MessageUtil.sendMessage(sender, "&7- &f" + who + " &8— &7" + why);
                if (++shown >= 20) {
                    MessageUtil.sendMessage(sender, "&8…and more");
                    break;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("ReportsCommand error: " + e.getMessage());
            MessageUtil.sendMessage(sender, "&cFailed to list reports. See console.");
        }
        return true;
    }
}
