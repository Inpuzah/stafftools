package com.inpuzah.stafftools.commands;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.UUID;

public class NotesCommand implements CommandExecutor {
    private final StaffToolsPlugin plugin;
    public NotesCommand(StaffToolsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) { MessageUtil.sendMessage(sender, "&cUsage: /" + label + " <player> [add <text...>|list]"); return true; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String action = (args.length >= 2) ? args[1].toLowerCase() : "list";

        var mgr = plugin.getNotesManager();

        if (action.equals("add")) {
            if (!sender.hasPermission("stafftools.notes.add")) { MessageUtil.sendMessage(sender, MessageUtil.getMessage("no-permission")); return true; }
            if (args.length < 3) { MessageUtil.sendMessage(sender, "&cUsage: /" + label + " <player> add <text...>"); return true; }

            String text = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            UUID staff = (sender instanceof Player p) ? p.getUniqueId() : UUID.fromString("00000000-0000-0000-0000-000000000000");

            mgr.addNote(
                    target.getUniqueId(),
                    target.getName() != null ? target.getName() : args[0],
                    staff,
                    sender.getName(),
                    text
            ).thenAccept(id -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (id > 0) {
                        MessageUtil.sendMessage(sender, plugin.getConfig().getString("messages.notes.added")
                                .replace("{player}", target.getName() != null ? target.getName() : args[0]));
                    } else {
                        MessageUtil.sendMessage(sender, "&cFailed to add note. See console.");
                    }
                });
            });
            return true;
        }

        // list
        if (!sender.hasPermission("stafftools.notes.list")) { MessageUtil.sendMessage(sender, MessageUtil.getMessage("no-permission")); return true; }
        mgr.getNotes(target.getUniqueId()).thenAccept(notes ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (notes == null || notes.isEmpty()) {
                        MessageUtil.sendMessage(sender, plugin.getConfig().getString("messages.notes.no-notes")
                                .replace("{player}", target.getName() != null ? target.getName() : args[0]));
                        return;
                    }

                    var header = plugin.getConfig().getString("messages.notes.list-header")
                            .replace("{player}", target.getName() != null ? target.getName() : args[0]);
                    MessageUtil.sendMessage(sender, header);

                    var format = plugin.getConfig().getString("messages.notes.list-format", "&7{id}. &f{note} &7- &e{staff} &7on &e{date}");
                    var sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
                    for (var n : notes) {
                        var line = format
                                .replace("{id}", String.valueOf(n.getId()))
                                .replace("{note}", n.getNote())
                                .replace("{staff}", n.getStaffName())
                                .replace("{date}", sdf.format(new java.util.Date(n.getTimestamp())));
                        MessageUtil.sendMessage(sender, line);
                    }
                })
        );
        return true;
    }
}
