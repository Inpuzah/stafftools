package com.inpuzah.stafftools.managers;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.utils.MessageUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class StaffModeManager {

    private final StaffToolsPlugin plugin;
    private final Set<UUID> staffMode;
    private final Map<UUID, ItemStack[]> savedInventories;
    private final Map<UUID, ItemStack[]> savedArmor;

    public StaffModeManager(StaffToolsPlugin plugin) {
        this.plugin = plugin;
        this.staffMode = new HashSet<>();
        this.savedInventories = new HashMap<>();
        this.savedArmor = new HashMap<>();
    }

    public void enableStaffMode(Player player) {
        if (isInStaffMode(player)) {
            return;
        }

        staffMode.add(player.getUniqueId());

        // Save inventory and armor
        savedInventories.put(player.getUniqueId(), player.getInventory().getContents());
        savedArmor.put(player.getUniqueId(), player.getInventory().getArmorContents());

        // Clear inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);

        // Give staff items
        if (plugin.getConfig().getBoolean("staff-mode.items.enabled", true)) {
            giveStaffItems(player);
        }

        // Auto-vanish
        if (plugin.getConfig().getBoolean("staff-mode.auto-vanish", true)) {
            plugin.getVanishManager().setVanished(player, true);
        }

        // Auto-fly
        if (plugin.getConfig().getBoolean("staff-mode.auto-fly", false)) {
            player.setAllowFlight(true);
            player.setFlying(true);
        }

        MessageUtil.sendMessage(player, MessageUtil.getMessage("staff-mode.enabled"));

        // Log to audit
        plugin.getAuditManager().logAction(
                player.getUniqueId(),
                player.getName(),
                "STAFF_MODE_ENABLE",
                null,
                null,
                "Staff mode enabled"
        );
    }

    public void disableStaffMode(Player player) {
        if (!isInStaffMode(player)) {
            return;
        }

        staffMode.remove(player.getUniqueId());

        // Restore inventory
        ItemStack[] inv = savedInventories.remove(player.getUniqueId());
        ItemStack[] armor = savedArmor.remove(player.getUniqueId());

        if (inv != null) {
            player.getInventory().setContents(inv);
        }
        if (armor != null) {
            player.getInventory().setArmorContents(armor);
        }

        // Disable vanish
        if (plugin.getVanishManager().isVanished(player)) {
            plugin.getVanishManager().setVanished(player, false);
        }

        // Disable fly if they don't have permission
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }

        MessageUtil.sendMessage(player, MessageUtil.getMessage("staff-mode.disabled"));

        // Log to audit
        plugin.getAuditManager().logAction(
                player.getUniqueId(),
                player.getName(),
                "STAFF_MODE_DISABLE",
                null,
                null,
                "Staff mode disabled"
        );
    }

    private void giveStaffItems(Player player) {
        // Compass - Teleporter
        if (plugin.getConfig().getBoolean("staff-mode.items.compass.enabled", true)) {
            int slot = plugin.getConfig().getInt("staff-mode.items.compass.slot", 0);
            String name = plugin.getConfig().getString("staff-mode.items.compass.name");
            List<String> lore = plugin.getConfig().getStringList("staff-mode.items.compass.lore");

            player.getInventory().setItem(slot, createItem(Material.COMPASS, name, lore));
        }

        // Freeze Stick
        if (plugin.getConfig().getBoolean("staff-mode.items.freeze-stick.enabled", true)) {
            int slot = plugin.getConfig().getInt("staff-mode.items.freeze-stick.slot", 1);
            String name = plugin.getConfig().getString("staff-mode.items.freeze-stick.name");
            List<String> lore = plugin.getConfig().getStringList("staff-mode.items.freeze-stick.lore");

            player.getInventory().setItem(slot, createItem(Material.STICK, name, lore));
        }

        // Inspector
        if (plugin.getConfig().getBoolean("staff-mode.items.inspector.enabled", true)) {
            int slot = plugin.getConfig().getInt("staff-mode.items.inspector.slot", 2);
            String name = plugin.getConfig().getString("staff-mode.items.inspector.name");
            List<String> lore = plugin.getConfig().getStringList("staff-mode.items.inspector.lore");

            player.getInventory().setItem(slot, createItem(Material.BOOK, name, lore));
        }

        // Random Teleport
        if (plugin.getConfig().getBoolean("staff-mode.items.random-tp.enabled", true)) {
            int slot = plugin.getConfig().getInt("staff-mode.items.random-tp.slot", 4);
            String name = plugin.getConfig().getString("staff-mode.items.random-tp.name");
            List<String> lore = plugin.getConfig().getStringList("staff-mode.items.random-tp.lore");

            player.getInventory().setItem(slot, createItem(Material.ENDER_PEARL, name, lore));
        }

        // Punish Book
        if (plugin.getConfig().getBoolean("staff-mode.items.punish-book.enabled", true)) {
            int slot = plugin.getConfig().getInt("staff-mode.items.punish-book.slot", 7);
            String name = plugin.getConfig().getString("staff-mode.items.punish-book.name");
            List<String> lore = plugin.getConfig().getStringList("staff-mode.items.punish-book.lore");

            player.getInventory().setItem(slot, createItem(Material.ENCHANTED_BOOK, name, lore));
        }

        // Vanish Toggle
        if (plugin.getConfig().getBoolean("staff-mode.items.vanish-toggle.enabled", true)) {
            int slot = plugin.getConfig().getInt("staff-mode.items.vanish-toggle.slot", 8);
            String name = plugin.getConfig().getString("staff-mode.items.vanish-toggle.name");
            List<String> lore = plugin.getConfig().getStringList("staff-mode.items.vanish-toggle.lore");

            player.getInventory().setItem(slot, createItem(Material.GRAY_DYE, name, lore));
        }
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(MessageUtil.component(name));

        List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(MessageUtil.component(line));
        }
        meta.lore(loreComponents);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isInStaffMode(Player player) {
        return staffMode.contains(player.getUniqueId());
    }

    public void disableAllStaffMode() {
        for (UUID uuid : new HashSet<>(staffMode)) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                disableStaffMode(player);
            }
        }
    }

    public Set<UUID> getStaffModePlayers() {
        return new HashSet<>(staffMode);
    }
}