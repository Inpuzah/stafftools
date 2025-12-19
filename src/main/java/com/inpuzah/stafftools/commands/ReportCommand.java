package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Report;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.UUID;

public class ReportCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;
    public ReportCommand(StaffToolsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, "&cUsage: /" + label + " <player> <reason...>");
            return true;
        }

        String reportedName = args[0];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        UUID reporterUuid = (sender instanceof Player p) ? p.getUniqueId() :
                UUID.fromString("00000000-0000-0000-0000-000000000000");
        String reporterName = sender.getName();

        // Try to resolve reported UUID from online/offline player (may be null)
        UUID reportedUuid = null;
        OfflinePlayer off = Bukkit.getOfflinePlayer(reportedName);
        if (off != null && (off.hasPlayedBefore() || off.isOnline())) {
            reportedUuid = off.getUniqueId();
            if (off.getName() != null) reportedName = off.getName();
        }

        try {
            // Use your 5-arg constructor
            Report report = new Report(reporterUuid, reporterName, reportedUuid, reportedName, reason);

            // Prefer createReport(Report)
            Object mgr = plugin.getReportManager();
            Method create = null;
            for (Method m : mgr.getClass().getMethods()) {
                if (m.getName().equals("createReport") && m.getParameterCount() == 1 &&
                        m.getParameterTypes()[0].equals(Report.class)) {
                    create = m; break;
                }
            }
            if (create != null) {
                create.invoke(mgr, report);
                MessageUtil.sendMessage(sender, "&aReport filed for &f" + reportedName + "&a: &7" + reason);
                return true;
            }

            // Fallback: any single-arg method that accepts Report
            for (Method m : mgr.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(Report.class)) {
                    m.invoke(mgr, report);
                    MessageUtil.sendMessage(sender, "&aReport filed for &f" + reportedName + "&a: &7" + reason);
                    return true;
                }
            }

            MessageUtil.sendMessage(sender, "&cReport manager has no suitable create method.");
        } catch (Exception e) {
            plugin.getLogger().warning("ReportCommand error: " + e.getMessage());
            MessageUtil.sendMessage(sender, "&cFailed to file report. See console.");
        }
        return true;
    }
}
