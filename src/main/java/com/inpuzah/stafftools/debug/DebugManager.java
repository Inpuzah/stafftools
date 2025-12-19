package com.inpuzah.stafftools.debug;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive debug logging system.
 * - Keeps a ring buffer of recent debug lines.
 * - Sends debug messages to Discord channel (rate-limited and filterable).
 * - Supports categorized logging with severity levels.
 */
public final class DebugManager {

    private final StaffToolsPlugin plugin;

    private boolean enabled;
    private int bufferSize;
    private boolean discordEnabled;
    private long discordMinIntervalMs;
    private String discordChannelId;
    private Set<String> discordCategories;
    private DebugLevel minDiscordLevel;
    private boolean logToConsole;

    private final Deque<DebugEntry> ring = new ArrayDeque<>();
    private final Map<String, Long> lastDiscordSend = new ConcurrentHashMap<>();

    // Discord reflection fields
    private Plugin discordRelay;
    private Object bridge;
    private Method getJdaMethod;

    public enum DebugLevel {
        TRACE(0, "TRACE", "🔍"),
        DEBUG(1, "DEBUG", "🐛"),
        INFO(2, "INFO", "ℹ️"),
        WARN(3, "WARN", "⚠️"),
        ERROR(4, "ERROR", "❌");

        private final int priority;
        private final String name;
        private final String emoji;

        DebugLevel(int priority, String name, String emoji) {
            this.priority = priority;
            this.name = name;
            this.emoji = emoji;
        }

        public int getPriority() {
            return priority;
        }

        public String getEmoji() {
            return emoji;
        }

        public boolean isAtLeast(DebugLevel other) {
            return this.priority >= other.priority;
        }
    }

    private static class DebugEntry {
        final Instant timestamp;
        final String category;
        final DebugLevel level;
        final String message;
        final String throwableSummary;

        DebugEntry(String category, DebugLevel level, String message, Throwable t) {
            this.timestamp = Instant.now();
            this.category = category;
            this.level = level;
            this.message = message;
            this.throwableSummary = t != null
                    ? (t.getClass().getSimpleName() + ": " + t.getMessage())
                    : null;
        }

        String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(DateTimeFormatter.ISO_INSTANT.format(timestamp)).append("] ");
            sb.append("[").append(level.name()).append("] ");
            sb.append("[").append(category).append("] ");
            sb.append(message);
            if (throwableSummary != null) {
                sb.append(" | ").append(throwableSummary);
            }
            return sb.toString();
        }

        String formatDiscord() {
            StringBuilder sb = new StringBuilder();
            sb.append(level.getEmoji()).append(" **").append(level.name()).append("** `[").append(category)
                    .append("]`\n");
            sb.append(message);
            if (throwableSummary != null) {
                sb.append("\n```\n").append(throwableSummary).append("\n```");
            }
            return sb.toString();
        }
    }

    public DebugManager(StaffToolsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("debug.enabled", false);
        this.bufferSize = Math.max(50, plugin.getConfig().getInt("debug.buffer-size", 250));
        this.logToConsole = plugin.getConfig().getBoolean("debug.log-to-console", true);

        this.discordEnabled = plugin.getConfig().getBoolean("debug.discord.enabled", false);
        this.discordMinIntervalMs = Math.max(250L, plugin.getConfig().getLong("debug.discord.rate-limit-ms", 2000L));
        this.discordChannelId = plugin.getConfig().getString("debug.discord.channel-id", "");

        // Parse categories to send to Discord
        List<String> categoryList = plugin.getConfig().getStringList("debug.discord.categories");
        this.discordCategories = categoryList.isEmpty()
                ? Set.of("ALL")
                : new HashSet<>(categoryList);

        // Parse minimum level for Discord
        String levelStr = plugin.getConfig().getString("debug.discord.min-level", "DEBUG");
        try {
            this.minDiscordLevel = DebugLevel.valueOf(levelStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.minDiscordLevel = DebugLevel.DEBUG;
        }

        trim();

        if (discordEnabled) {
            setupDiscordRelay();
        }
    }

    private void setupDiscordRelay() {
        discordRelay = Bukkit.getPluginManager().getPlugin("DiscordRelay");

        if (discordRelay == null) {
            plugin.getLogger().warning("Debug Discord integration enabled but DiscordRelay plugin not found!");
            discordEnabled = false;
            return;
        }

        try {
            Method getDiscordMethod = discordRelay.getClass().getDeclaredMethod("getDiscord");
            bridge = getDiscordMethod.invoke(discordRelay);
            Class<?> bridgeClass = bridge.getClass();
            getJdaMethod = bridgeClass.getDeclaredMethod("getJda");

            plugin.getLogger().info("Debug Discord integration enabled.");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to setup Debug Discord integration: " + e.getMessage());
            discordEnabled = false;
        }
    }

    private Object getDebugChannel() {
        if (!discordEnabled || bridge == null || discordChannelId == null || discordChannelId.isEmpty()) {
            return null;
        }

        try {
            Object jda = getJdaMethod.invoke(bridge);
            Method getTextChannelById = jda.getClass().getMethod("getTextChannelById", String.class);
            return getTextChannelById.invoke(jda, discordChannelId);
        } catch (Exception e) {
            return null;
        }
    }

    private void sendToDiscord(DebugEntry entry) {
        if (!discordEnabled)
            return;

        // Check if level meets minimum
        if (!entry.level.isAtLeast(minDiscordLevel))
            return;

        // Check if category is allowed
        if (!discordCategories.contains("ALL") && !discordCategories.contains(entry.category)) {
            return;
        }

        // Rate limiting per category
        String key = entry.category + ":" + entry.level.name();
        Long lastSend = lastDiscordSend.get(key);
        long now = System.currentTimeMillis();
        if (lastSend != null && (now - lastSend) < discordMinIntervalMs) {
            return; // Too soon, skip
        }

        lastDiscordSend.put(key, now);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Object channel = getDebugChannel();
            if (channel == null)
                return;

            try {
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

                Object action = sendMessage.invoke(channel, entry.formatDiscord());
                Method queue = action.getClass().getMethod("queue");
                queue.invoke(action);
            } catch (Exception e) {
                // Silent failure to avoid log spam
            }
        });
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // Legacy compatibility methods
    public void log(String category, String message) {
        log(category, DebugLevel.DEBUG, message, null);
    }

    public void log(String category, String message, Throwable t) {
        log(category, DebugLevel.DEBUG, message, t);
    }

    // New level-based logging methods
    public void trace(String category, String message) {
        log(category, DebugLevel.TRACE, message, null);
    }

    public void debug(String category, String message) {
        log(category, DebugLevel.DEBUG, message, null);
    }

    public void info(String category, String message) {
        log(category, DebugLevel.INFO, message, null);
    }

    public void warn(String category, String message) {
        log(category, DebugLevel.WARN, message, null);
    }

    public void warn(String category, String message, Throwable t) {
        log(category, DebugLevel.WARN, message, t);
    }

    public void error(String category, String message) {
        log(category, DebugLevel.ERROR, message, null);
    }

    public void error(String category, String message, Throwable t) {
        log(category, DebugLevel.ERROR, message, t);
    }

    private void log(String category, DebugLevel level, String message, Throwable t) {
        if (!enabled)
            return;

        DebugEntry entry = new DebugEntry(category, level, message, t);

        synchronized (ring) {
            ring.addLast(entry);
            trim();
        }

        // Log to console if enabled
        if (logToConsole) {
            String logLevel = level.getPriority() >= DebugLevel.ERROR.getPriority() ? "SEVERE"
                    : level.getPriority() >= DebugLevel.WARN.getPriority() ? "WARNING"
                            : "INFO";

            switch (logLevel) {
                case "SEVERE" -> plugin.getLogger().severe("[DEBUG] " + entry.format());
                case "WARNING" -> plugin.getLogger().warning("[DEBUG] " + entry.format());
                default -> plugin.getLogger().info("[DEBUG] " + entry.format());
            }
        }

        // Send to Discord if configured
        sendToDiscord(entry);
    }

    public List<String> dump(int maxLines) {
        int n = Math.max(1, maxLines);
        synchronized (ring) {
            int start = Math.max(0, ring.size() - n);
            List<String> out = new ArrayList<>(n);
            int i = 0;
            for (DebugEntry entry : ring) {
                if (i++ >= start)
                    out.add(entry.format());
            }
            return out;
        }
    }

    public List<String> dumpByCategory(String category, int maxLines) {
        int n = Math.max(1, maxLines);
        synchronized (ring) {
            List<String> out = new ArrayList<>();
            for (DebugEntry entry : ring) {
                if (entry.category.equalsIgnoreCase(category)) {
                    out.add(entry.format());
                    if (out.size() >= n)
                        break;
                }
            }
            return out;
        }
    }

    public List<String> dumpByLevel(DebugLevel minLevel, int maxLines) {
        int n = Math.max(1, maxLines);
        synchronized (ring) {
            List<String> out = new ArrayList<>();
            for (DebugEntry entry : ring) {
                if (entry.level.isAtLeast(minLevel)) {
                    out.add(entry.format());
                    if (out.size() >= n)
                        break;
                }
            }
            return out;
        }
    }

    public void sendDumpTo(CommandSender sender, int maxLines) {
        List<String> lines = dump(maxLines);
        MessageUtil.sendMessage(sender, "&eStaffTools Debug Dump (&f" + lines.size() + "&e lines)");
        for (String line : lines) {
            MessageUtil.sendMessageWithoutPrefix(sender, "&7" + line);
        }
    }

    public void sendCategoryDumpTo(CommandSender sender, String category, int maxLines) {
        List<String> lines = dumpByCategory(category, maxLines);
        MessageUtil.sendMessage(sender, "&eDebug Dump [" + category + "] (&f" + lines.size() + "&e lines)");
        for (String line : lines) {
            MessageUtil.sendMessageWithoutPrefix(sender, "&7" + line);
        }
    }

    public Set<String> getCategories() {
        synchronized (ring) {
            Set<String> categories = new HashSet<>();
            for (DebugEntry entry : ring) {
                categories.add(entry.category);
            }
            return categories;
        }
    }

    private void trim() {
        synchronized (ring) {
            while (ring.size() > bufferSize) {
                ring.removeFirst();
            }
        }
    }
}
