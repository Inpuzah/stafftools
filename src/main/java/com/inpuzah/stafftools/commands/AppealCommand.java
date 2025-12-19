package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Appeal;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public class AppealCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;
    public AppealCommand(StaffToolsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { MessageUtil.sendMessage(sender, "&cOnly in-game."); return true; }
        if (args.length < 2) { MessageUtil.sendMessage(sender, "&cUsage: /" + label + " <punishment-id> <appeal reason...>"); return true; }

        int punishmentId;
        try { punishmentId = Integer.parseInt(args[0]); }
        catch (NumberFormatException e) { MessageUtil.sendMessage(sender, "&cInvalid punishment ID."); return true; }

        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        try {
            // Use your 4-arg constructor
            Appeal appeal = new Appeal(punishmentId, p.getUniqueId(), p.getName(), text);

            Object mgr = plugin.getAppealManager();

            // Prefer submitAppeal(Appeal)
            Method submit = null;
            for (Method m : mgr.getClass().getMethods()) {
                if (m.getName().equals("submitAppeal") && m.getParameterCount() == 1 &&
                        m.getParameterTypes()[0].equals(Appeal.class)) { submit = m; break; }
            }
            if (submit != null) {
                submit.invoke(mgr, appeal);
                MessageUtil.sendMessage(sender, "&aAppeal submitted for #" + punishmentId + ".");
                return true;
            }

            // Fallback: any single-arg method that accepts Appeal
            for (Method m : mgr.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(Appeal.class)) {
                    m.invoke(mgr, appeal);
                    MessageUtil.sendMessage(sender, "&aAppeal submitted for #" + punishmentId + ".");
                    return true;
                }
            }

            MessageUtil.sendMessage(sender, "&cAppeal manager has no suitable submit method.");
        } catch (Exception e) {
            plugin.getLogger().warning("AppealCommand error: " + e.getMessage());
            MessageUtil.sendMessage(sender, "&cFailed to submit appeal. See console.");
        }
        return true;
    }
}
