package com.inpuzah.stafftools.gui;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Punishment;
import com.inpuzah.stafftools.utils.MessageUtil;
import com.inpuzah.stafftools.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PunishmentHistoryGUI {

    private final StaffToolsPlugin plugin;
    private final Player viewer;
    private final Player target;
    @NotNull
    private final Inventory inventory;
    private List<Punishment> punishments;
    private int page = 0;
    private static final int ITEMS_PER_PAGE = 45;

    public PunishmentHistoryGUI(StaffToolsPlugin plugin, Player viewer, Player target) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.target = target;
        this.inventory = Bukkit.createInventory(
                null,
                54,
                MessageUtil.component("&e&lHistory: &f" + target.getName()));

        loadHistory();
    }

    private void loadHistory() {
        plugin.getPunishmentManager().getPlayerHistory(target.getUniqueId()).thenAccept(history -> {
            this.punishments = history;
            Bukkit.getScheduler().runTask(plugin, this::setupGUI);
        });
    }

    private void setupGUI() {
        inventory.clear();

        if (punishments == null || punishments.isEmpty()) {
            ItemStack noHistory = createItem(
                    Material.BARRIER,
                    "&c&lNo History",
                    "&7This player has no",
                    "&7punishment history");
            inventory.setItem(22, noHistory);
            addNavigationButtons();
            return;
        }

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, punishments.size());

        for (int i = start; i < end; i++) {
            Punishment punishment = punishments.get(i);
            inventory.setItem(i - start, createPunishmentItem(punishment));
        }

        addNavigationButtons();
    }

    private ItemStack createPunishmentItem(Punishment punishment) {
        Material material = switch (punishment.getType()) {
            case WARN -> Material.PAPER;
            case MUTE -> Material.REDSTONE_BLOCK;
            case KICK -> Material.IRON_DOOR;
            case BAN -> Material.BARRIER;
            case BUILDBAN -> Material.BRICKS; // pick any clear icon for build bans
        };

        String color = punishment.isActive() ? "&c" : "&7";
        String name = color + "&l" + punishment.getType().name();

        List<String> lore = new ArrayList<>();
        lore.add("&7Reason: &f" + punishment.getReason());
        lore.add("&7Staff: &f" + punishment.getStaffName());
        lore.add("&7Date: &f" + TimeUtil.formatDate(punishment.getTimestamp()));

        if (punishment.getDuration() > 0) {
            lore.add("&7Duration: &f" + TimeUtil.formatDuration(punishment.getDuration()));
            if (punishment.isActive() && !punishment.isExpired()) {
                lore.add("&7Remaining: &f" + TimeUtil.formatDurationMillis(punishment.getRemainingTime()));
            }
        } else {
            lore.add("&7Duration: &fPermanent");
        }

        lore.add("&7Status: " + (punishment.isActive() ? "&a&lACTIVE" : "&7Inactive"));

        if (punishment.getRemovedBy() != null) {
            String removedByName = Bukkit.getOfflinePlayer(punishment.getRemovedBy()).getName();
            lore.add("&7Removed by: &f" + (removedByName != null ? removedByName : "Unknown"));
            if (punishment.getRemovedReason() != null) {
                lore.add("&7Remove reason: &f" + punishment.getRemovedReason());
            }
        }

        if (punishment.isActive() && viewer.hasPermission("stafftools.punish.remove")) {
            lore.add("");
            lore.add("&e&lClick to remove");
        }

        return createItem(material, name, lore.toArray(new String[0]));
    }

    private void addNavigationButtons() {
        int maxPages = getMaxPages();

        // Previous page
        if (page > 0) {
            ItemStack prev = createItem(
                    Material.ARROW,
                    "&e&lPrevious Page",
                    "&7Page " + page + "/" + maxPages);
            inventory.setItem(48, prev);
        }

        // Next page
        if (page < maxPages - 1) {
            ItemStack next = createItem(
                    Material.ARROW,
                    "&e&lNext Page",
                    "&7Page " + (page + 2) + "/" + maxPages);
            inventory.setItem(50, next);
        }

        // Back button
        ItemStack back = createItem(Material.BARRIER, "&c&lBack", "&7Return to punishment menu");
        inventory.setItem(49, back);

        // Info item
        int total = punishments != null ? punishments.size() : 0;
        long active = punishments != null ? punishments.stream().filter(Punishment::isActive).count() : 0;
        long inactive = total - active;

        ItemStack info = createItem(
                Material.BOOK,
                "&b&lPunishment History",
                "&7Total Punishments: &f" + total,
                "&7Active: &a" + active,
                "&7Inactive: &7" + inactive);
        inventory.setItem(45, info);
    }

    private int getMaxPages() {
        int total = punishments != null ? punishments.size() : 0;
        return Math.max(1, (int) Math.ceil((double) total / ITEMS_PER_PAGE));
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.component(name));

        List<Component> loreList = new ArrayList<>();
        for (String line : lore) {
            if (line != null && !line.isEmpty()) {
                loreList.add(MessageUtil.component(line));
            }
        }
        meta.lore(loreList);

        item.setItemMeta(meta);
        return item;
    }

    public void handleClick(int slot) {
        if (slot == 48 && page > 0) {
            page--;
            setupGUI();
        } else if (slot == 50 && page < getMaxPages() - 1) {
            page++;
            setupGUI();
        } else if (slot == 49) {
            viewer.closeInventory();
            new PunishmentGUI(plugin, viewer, target).open();
        } else if (slot < ITEMS_PER_PAGE && punishments != null) {
            int index = page * ITEMS_PER_PAGE + slot;
            if (index < punishments.size()) {
                Punishment punishment = punishments.get(index);
                if (punishment.isActive() && viewer.hasPermission("stafftools.punish.remove")) {
                    // TODO: Open confirmation GUI or remove directly
                    viewer.closeInventory();
                    viewer.performCommand("punishremove " + punishment.getId());
                }
            }
        }
    }

    public void open() {
        GuiRegistry.register(viewer, inventory, this);
        @SuppressWarnings("nullness")
        var opened = viewer.openInventory(inventory);
    }

    public Inventory getInventory() {
        return inventory;
    }
}
