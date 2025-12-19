package com.inpuzah.stafftools.chat;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.utils.MessageUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight prompt manager to collect a single line of input from chat.
 * Used for "write-in" fields in GUIs (custom reason / custom duration).
 */
public final class ChatPromptManager {

    public interface Prompt {
        void onInput(String input);

        default void onCancel() {
        }
    }

    private final StaffToolsPlugin plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public ChatPromptManager(StaffToolsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasPrompt(UUID playerUuid) {
        return prompts.containsKey(playerUuid);
    }

    public void begin(Player player, String instructions, Prompt prompt) {
        if (player == null)
            return;

        prompts.put(player.getUniqueId(), prompt);

        MessageUtil.sendMessage(player, "&eChat input mode: &f" + instructions);
        MessageUtil.sendMessage(player, "&7Type &fcancel&7 to cancel.");
    }

    /**
     * Returns true if the chat event was consumed by an active prompt.
     */
    public boolean handle(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Prompt prompt = prompts.get(player.getUniqueId());
        if (prompt == null)
            return false;

        event.setCancelled(true);

        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        prompts.remove(player.getUniqueId());

        if (input.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    prompt.onCancel();
                } catch (Exception ignored) {
                }
                MessageUtil.sendMessage(player, "&7Cancelled.");
            });
            return true;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                prompt.onInput(input);
            } catch (Exception e) {
                plugin.getLogger().warning("Chat prompt failed: " + e.getMessage());
            }
        });

        return true;
    }
}
