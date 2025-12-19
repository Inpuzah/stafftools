package com.inpuzah.stafftools.utils;

import com.inpuzah.stafftools.StaffToolsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Message helper.
 *
 * This plugin's config uses legacy '&' color codes. Paper's Adventure APIs expect Components;
 * if you pass '§' color codes inside Component.text(...), clients will see the raw characters.
 */
public final class MessageUtil {

    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {}

    /** Converts legacy '&' colors to section sign for legacy String APIs. */
    public static String colorize(String message) {
        if (message == null) return "";
        return message.replace('&', '§');
    }

    /** Deserialize legacy '&' colors into an Adventure Component. */
    public static Component component(String legacyMessage) {
        if (legacyMessage == null) legacyMessage = "";
        return LEGACY_AMP.deserialize(legacyMessage);
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) return;

        String full = getPrefix() + message;
        if (sender instanceof Player p) {
            p.sendMessage(component(full));
        } else {
            sender.sendMessage(colorize(full));
        }
    }

    public static void sendMessageWithoutPrefix(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) return;

        if (sender instanceof Player p) {
            p.sendMessage(component(message));
        } else {
            sender.sendMessage(colorize(message));
        }
    }

    public static void sendActionBar(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) return;
        player.sendActionBar(component(message));
    }

    public static String getPrefix() {
        return StaffToolsPlugin.getInstance().getConfig().getString("messages.prefix", "&7[&c&lStaff&7] ");
    }

    public static String getMessage(String path) {
        return StaffToolsPlugin.getInstance().getConfig().getString("messages." + path, "Message not found: " + path);
    }
}
