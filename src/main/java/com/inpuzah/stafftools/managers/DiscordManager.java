package com.inpuzah.stafftools.managers;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.*;
import com.inpuzah.stafftools.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Discord integration manager - interfaces with DiscordRelay plugin (optional).
 * This version avoids compile-time dependency on DiscordRelay by using
 * reflection.
 */
public class DiscordManager {

    private final StaffToolsPlugin plugin;
    private Plugin discordRelay;
    private Object bridge; // was DiscordBridge
    private boolean enabled;
    private Method getJdaMethod;
    private String staffChannelId;

    public DiscordManager(StaffToolsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Reloads config values and (re)binds to DiscordRelay if enabled. */
    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("discord.enabled", false);

        // Clear any previous reflective handles.
        this.discordRelay = null;
        this.bridge = null;
        this.getJdaMethod = null;
        this.staffChannelId = null;

        if (enabled) {
            setupDiscordRelay();
        }
    }

    private void setupDiscordRelay() {
        discordRelay = Bukkit.getPluginManager().getPlugin("DiscordRelay");

        if (discordRelay == null) {
            plugin.getLogger().warning("Discord integration enabled but DiscordRelay plugin not found!");
            enabled = false;
            return;
        }

        try {
            // Get the DiscordBridge instance from DiscordRelay's Main class via reflection
            Method getDiscordMethod = discordRelay.getClass().getDeclaredMethod("getDiscord");
            bridge = getDiscordMethod.invoke(discordRelay);

            // Obtain JDA from the bridge via reflection
            Class<?> bridgeClass = bridge.getClass();
            getJdaMethod = bridgeClass.getDeclaredMethod("getJda");

            staffChannelId = plugin.getConfig().getString("discord.channels.staff-notifications");
            plugin.getLogger().info("Discord integration enabled with DiscordRelay (reflective).");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to setup Discord integration: " + e.getMessage());
            enabled = false;
        }
    }

    private Object getStaffChannel() {
        if (!enabled || bridge == null || staffChannelId == null || staffChannelId.isEmpty()) {
            return null;
        }

        try {
            Object jda = getJdaMethod.invoke(bridge);
            Method getTextChannelById = jda.getClass().getMethod("getTextChannelById", String.class);
            return getTextChannelById.invoke(jda, staffChannelId);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get staff channel: " + e.getMessage());
            return null;
        }
    }

    private void sendPlain(Object channel, String content) {
        try {
            // TextChannel#sendMessage(String) -> MessageAction, then #queue()
            Method sendMessage = null;
            for (Method m : channel.getClass().getMethods()) {
                if (!m.getName().equals("sendMessage"))
                    continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 1
                        && (params[0] == String.class || CharSequence.class.isAssignableFrom(params[0]))) {
                    sendMessage = m;
                    break;
                }
            }
            if (sendMessage == null)
                return;

            Object action = sendMessage.invoke(channel, content);
            Method queue = action.getClass().getMethod("queue");
            queue.invoke(action);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send Discord message: " + e.getMessage());
        }
    }

    /** Send punishment notification to Discord */
    public void sendPunishmentNotification(Punishment punishment) {
        if (!enabled || !plugin.getConfig().getBoolean("discord.events.punishment", true)) {
            plugin.getLogger().info("[Discord] Punishment notification skipped (enabled=" + enabled + ")");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Object channel = getStaffChannel();
            if (channel == null) {
                plugin.getLogger().warning("[Discord] Staff channel not found or invalid");
                return;
            }

            String text = "**Punishment Issued**\n"
                    + "Player: **" + punishment.getPlayerName() + "**\n"
                    + "Type: **" + punishment.getType().name() + "**\n"
                    + "Duration: **"
                    + (punishment.isPermanent() ? "Permanent" : TimeUtil.formatDuration(punishment.getDuration()))
                    + "**\n"
                    + "Reason: " + punishment.getReason() + "\n"
                    + "Staff: **" + punishment.getStaffName() + "**\n"
                    + "ID: #" + punishment.getId();

            sendPlain(channel, text);
            plugin.getLogger().info("[Discord] Sent punishment notification for " + punishment.getPlayerName());
        });
    }

    /** Send report notification to Discord */
    public void sendReportNotification(Report report) {
        if (!enabled || !plugin.getConfig().getBoolean("discord.events.report", true)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Object channel = getStaffChannel();
            if (channel == null)
                return;

            String text = "**Player Report**\n"
                    + "Reported: **" + report.getReportedName() + "**\n"
                    + "Reporter: **" + report.getReporterName() + "**\n"
                    + "Status: **" + report.getStatus().name() + "**\n"
                    + "Reason: " + report.getReason() + "\n"
                    + "ID: #" + report.getId();

            sendPlain(channel, text);
        });
    }

    /** Send appeal notification to Discord */
    public void sendAppealNotification(Appeal appeal, Punishment punishment) {
        if (!enabled || !plugin.getConfig().getBoolean("discord.events.appeal", true)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Object channel = getStaffChannel();
            if (channel == null)
                return;

            String text = "**Punishment Appeal**\n"
                    + "Player: **" + appeal.getPlayerName() + "**\n"
                    + "Punishment: **" + punishment.getType().name() + "**\n"
                    + "Status: **" + appeal.getStatus().name() + "**\n"
                    + "Original reason: " + punishment.getReason() + "\n"
                    + "Appeal: " + appeal.getAppealText() + "\n"
                    + "Appeal ID: #" + appeal.getId() + " | Punishment ID: #" + punishment.getId();

            sendPlain(channel, text);
        });
    }

    /** Send BuildBan notification (uses BGSU gold by default) */
    public void sendBuildBanNotification(Punishment buildban) {
        if (!enabled || !plugin.getConfig().getBoolean("discord.events.buildban", true)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Object channel = getStaffChannel();
            if (channel == null)
                return;

            String text = "**BuildBan Issued**\n"
                    + "Player: **" + buildban.getPlayerName() + "**\n"
                    + "Duration: **"
                    + (buildban.isPermanent() ? "Permanent" : TimeUtil.formatDuration(buildban.getDuration())) + "**\n"
                    + "Reason: " + buildban.getReason() + "\n"
                    + "Staff: **" + buildban.getStaffName() + "**\n"
                    + "ID: #" + buildban.getId();

            sendPlain(channel, text);
        });
    }

    /** Send staff announcement */
    public void sendStaffAnnouncement(String staffName, String message, boolean isWarning) {
        if (!enabled) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Object channel = getStaffChannel();
            if (channel == null)
                return;

            String text = (isWarning ? "**Staff Warning**\n" : "**Staff Announcement**\n")
                    + (staffName != null ? ("By: **" + staffName + "**\n") : "")
                    + message;

            sendPlain(channel, text);
        });
    }

    /** Is Discord integration enabled and usable? */
    public boolean isEnabled() {
        return enabled && bridge != null && discordRelay != null;
    }

    /** Get the DiscordRelay plugin instance (nullable) */
    public Plugin getDiscordRelay() {
        return discordRelay;
    }
}
