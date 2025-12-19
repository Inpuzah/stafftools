package com.inpuzah.stafftools.gui;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Punishment;
import com.inpuzah.stafftools.database.models.PunishmentType;
import com.inpuzah.stafftools.utils.MessageUtil;
import com.inpuzah.stafftools.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PunishmentGUI {

    private final StaffToolsPlugin plugin;
    private final Player staff;
    private final Player target;
    @NotNull
    private final Inventory inventory;

    private PunishmentType selectedType;
    private String selectedReason;
    private long selectedDuration;

    private static final int CUSTOM_REASON_SLOT = 26;
    private static final int CUSTOM_DURATION_SLOT = 35;

    public PunishmentGUI(StaffToolsPlugin plugin, Player staff, Player target) {
        this(plugin, staff, target, null, null, -1);
    }

    public PunishmentGUI(StaffToolsPlugin plugin,
            Player staff,
            Player target,
            PunishmentType selectedType,
            String selectedReason,
            long selectedDuration) {
        this.plugin = plugin;
        this.staff = staff;
        this.target = target;
        this.selectedType = selectedType;
        this.selectedReason = selectedReason;
        this.selectedDuration = selectedDuration;
        this.inventory = Bukkit.createInventory(null, 54, MessageUtil.component("&c&lPunish: &f" + target.getName()));
        setupGUI();
    }

    private void setupGUI() {
        // Player head
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        headMeta.setOwningPlayer(target);
        headMeta.displayName(MessageUtil.component("&e" + target.getName()));
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtil.component("&7Click punishment type below"));
        headMeta.lore(lore);
        head.setItemMeta(headMeta);
        inventory.setItem(4, head);

        setupPunishmentTypes();
        setupPresetReasons();
        setupDurationPresets();

        // History
        ItemStack history = createItem(Material.BOOK, "&e&lView History",
                "&7Click to view punishment", "&7history for this player");
        inventory.setItem(49, history);

        // Execute / Cancel
        updateExecuteButton();
        ItemStack cancel = createItem(Material.BARRIER, "&c&lCancel", "&7Click to close");
        inventory.setItem(45, cancel);

        // Fillers
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(""));
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null)
                inventory.setItem(i, filler);
        }
    }

    private void setupPunishmentTypes() {
        if (staff.hasPermission("stafftools.punish.warn")) {
            ItemStack warn = createItem(Material.PAPER, "&e&lWarn", "&7Issue a warning",
                    "", selectedType == PunishmentType.WARN ? "&a&l✓ SELECTED" : "&7Click to select");
            inventory.setItem(10, warn);
        }
        if (staff.hasPermission("stafftools.punish.mute")) {
            ItemStack mute = createItem(Material.REDSTONE_BLOCK, "&c&lMute", "&7Prevent chatting",
                    "", selectedType == PunishmentType.MUTE ? "&a&l✓ SELECTED" : "&7Click to select");
            inventory.setItem(12, mute);
        }
        if (staff.hasPermission("stafftools.punish.kick")) {
            ItemStack kick = createItem(Material.IRON_DOOR, "&6&lKick", "&7Remove from server",
                    "", selectedType == PunishmentType.KICK ? "&a&l✓ SELECTED" : "&7Click to select");
            inventory.setItem(14, kick);
        }
        if (staff.hasPermission("stafftools.punish.ban")) {
            ItemStack ban = createItem(Material.BARRIER, "&4&lBan", "&7Temp or permanent ban",
                    "", selectedType == PunishmentType.BAN ? "&a&l✓ SELECTED" : "&7Click to select");
            inventory.setItem(16, ban);
        }
        // NEW: BUILDBAN button (slot 18)
        if (staff.hasPermission("stafftools.punish.buildban")) {
            ItemStack buildban = createItem(Material.BRICKS, "&6&lBuildBan",
                    "&7Explore/chat allowed; no building", "",
                    selectedType == PunishmentType.BUILDBAN ? "&a&l✓ SELECTED" : "&7Click to select");
            inventory.setItem(18, buildban);
        }
    }

    private void setupPresetReasons() {
        List<String> reasons = plugin.getConfig().getStringList("punishment.preset-reasons");
        int slot = 19;
        for (int i = 0; i < Math.min(reasons.size(), 7); i++) {
            String reason = reasons.get(i);
            ItemStack item = createItem(Material.WRITABLE_BOOK, "&f" + reason, "",
                    selectedReason != null && selectedReason.equals(reason) ? "&a&l✓ SELECTED" : "&7Click to select");
            inventory.setItem(slot++, item);
        }

        // Write-in reason
        ItemStack customReason = createItem(Material.NAME_TAG,
                "&e&lCustom Reason",
                "&7Click to type a",
                "&7custom reason in chat",
                "",
                selectedReason != null && !reasons.contains(selectedReason) ? "&a&l✓ SELECTED" : "&7Click to select");
        inventory.setItem(CUSTOM_REASON_SLOT, customReason);
    }

    private List<Map.Entry<String, Integer>> getDurationPresetsOrdered() {
        var section = plugin.getConfig().getConfigurationSection("punishment.duration-presets");
        if (section == null)
            return java.util.List.of();

        // Preserve YAML order.
        return section.getValues(false).entrySet().stream()
                .map(e -> Map.entry(e.getKey(), (Integer) e.getValue()))
                .toList();
    }

    private void setupDurationPresets() {
        List<Map.Entry<String, Integer>> durations = getDurationPresetsOrdered();
        int slot = 28;
        for (int i = 0; i < Math.min(durations.size(), 7); i++) {
            Map.Entry<String, Integer> entry = durations.get(i);
            String name = entry.getKey();
            long minutes = entry.getValue();
            ItemStack item = createItem(Material.CLOCK, "&b" + name,
                    "&7Duration: &f" + (minutes == 0 ? "Permanent" : TimeUtil.formatDuration(minutes)), "",
                    selectedDuration == minutes ? "&a&l✓ SELECTED" : "&7Click to select");
            inventory.setItem(slot++, item);
        }

        // Write-in duration
        ItemStack customDuration = createItem(Material.WRITABLE_BOOK,
                "&e&lCustom Duration",
                "&7Click to type a",
                "&7custom duration in chat",
                "&7Examples: &f30m&7, &f1h&7, &f7d&7, &fperm");
        inventory.setItem(CUSTOM_DURATION_SLOT, customDuration);
    }

    private void updateExecuteButton() {
        boolean canExecute = selectedType != null && selectedReason != null;
        if (selectedType == PunishmentType.MUTE || selectedType == PunishmentType.BAN
                || selectedType == PunishmentType.BUILDBAN) {
            canExecute = canExecute && selectedDuration >= 0;
        }
        String durationLine;
        if (selectedType == PunishmentType.MUTE || selectedType == PunishmentType.BAN
                || selectedType == PunishmentType.BUILDBAN) {
            durationLine = "&7Duration: &f"
                    + (selectedDuration == 0 ? "Permanent" : TimeUtil.formatDuration(selectedDuration));
        } else {
            durationLine = "&7Duration: &fN/A";
        }

        ItemStack execute = canExecute
                ? createItem(Material.LIME_CONCRETE, "&a&lExecute Punishment",
                        "&7Type: &f" + selectedType.name(),
                        "&7Reason: &f" + selectedReason,
                        durationLine,
                        "", "&a&lCLICK TO EXECUTE")
                : createItem(Material.RED_CONCRETE, "&c&lSelect All Options",
                        "&7You must select:",
                        "&7- Punishment Type",
                        "&7- Reason",
                        (selectedType == PunishmentType.MUTE || selectedType == PunishmentType.BAN
                                || selectedType == PunishmentType.BUILDBAN) ? "&7- Duration" : "");
        inventory.setItem(53, execute);
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.component(name));
        List<Component> loreList = new ArrayList<>();
        for (String line : lore)
            if (!line.isEmpty())
                loreList.add(MessageUtil.component(line));
        meta.lore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    public void handleClick(int slot) {
        if (slot == 10 && staff.hasPermission("stafftools.punish.warn")) {
            selectedType = PunishmentType.WARN;
            selectedDuration = 0;
            setupGUI();
        } else if (slot == 12 && staff.hasPermission("stafftools.punish.mute")) {
            selectedType = PunishmentType.MUTE;
            selectedDuration = -1;
            setupGUI();
        } else if (slot == 14 && staff.hasPermission("stafftools.punish.kick")) {
            selectedType = PunishmentType.KICK;
            selectedDuration = 0;
            setupGUI();
        } else if (slot == 16 && staff.hasPermission("stafftools.punish.ban")) {
            selectedType = PunishmentType.BAN;
            selectedDuration = -1;
            setupGUI();
        } else if (slot == 18 && staff.hasPermission("stafftools.punish.buildban")) { // NEW: BUILDBAN
            selectedType = PunishmentType.BUILDBAN;
            selectedDuration = -1;
            setupGUI();
        } else if (slot >= 19 && slot <= 25) {
            List<String> reasons = plugin.getConfig().getStringList("punishment.preset-reasons");
            int index = slot - 19;
            if (index < reasons.size()) {
                selectedReason = reasons.get(index);
                setupGUI();
            }
        } else if (slot >= 28 && slot <= 34) {
            int index = slot - 28;
            List<Map.Entry<String, Integer>> durations = getDurationPresetsOrdered();
            if (index < durations.size()) {
                selectedDuration = durations.get(index).getValue();
                setupGUI();
            }
        } else if (slot == CUSTOM_REASON_SLOT) {
            // Close GUI and prompt for a custom reason.
            staff.closeInventory();
            var pluginRef = this.plugin;
            var targetRef = this.target;
            var typeRef = this.selectedType;
            var durationRef = this.selectedDuration;
            plugin.getChatPromptManager().begin(staff, "custom reason",
                    new com.inpuzah.stafftools.chat.ChatPromptManager.Prompt() {
                        @Override
                        public void onInput(String input) {
                            new PunishmentGUI(pluginRef, staff, targetRef, typeRef, input, durationRef).open();
                        }

                        @Override
                        public void onCancel() {
                            new PunishmentGUI(pluginRef, staff, targetRef, typeRef, selectedReason, durationRef).open();
                        }
                    });
        } else if (slot == CUSTOM_DURATION_SLOT) {
            // Close GUI and prompt for a custom duration.
            staff.closeInventory();
            var pluginRef = this.plugin;
            var targetRef = this.target;
            var typeRef = this.selectedType;
            var reasonRef = this.selectedReason;
            plugin.getChatPromptManager().begin(staff, "custom duration (e.g. 30m, 1h, 7d, perm)",
                    new com.inpuzah.stafftools.chat.ChatPromptManager.Prompt() {
                        @Override
                        public void onInput(String input) {
                            long mins = TimeUtil.parseDuration(input);
                            if (mins < 0) {
                                MessageUtil.sendMessage(staff, "&cInvalid duration. Try again.");
                                new PunishmentGUI(pluginRef, staff, targetRef, typeRef, reasonRef, selectedDuration)
                                        .open();
                                return;
                            }
                            new PunishmentGUI(pluginRef, staff, targetRef, typeRef, reasonRef, mins).open();
                        }

                        @Override
                        public void onCancel() {
                            new PunishmentGUI(pluginRef, staff, targetRef, typeRef, reasonRef, selectedDuration).open();
                        }
                    });
        } else if (slot == 49) {
            staff.closeInventory();
            new PunishmentHistoryGUI(plugin, staff, target).open();
        } else if (slot == 53) {
            executePunishment();
        } else if (slot == 45) {
            staff.closeInventory();
        }
    }

    private void executePunishment() {
        if (selectedType == null || selectedReason == null) {
            MessageUtil.sendMessage(staff, "&cPlease select all required options!");
            return;
        }
        if ((selectedType == PunishmentType.MUTE || selectedType == PunishmentType.BAN
                || selectedType == PunishmentType.BUILDBAN)
                && selectedDuration < 0) {
            MessageUtil.sendMessage(staff, "&cPlease select a duration!");
            return;
        }
        staff.closeInventory();

        Punishment punishment = new Punishment(
                target.getUniqueId(),
                target.getName(),
                staff.getUniqueId(),
                staff.getName(),
                selectedType,
                selectedReason,
                selectedDuration);
        punishment.setServerName(Bukkit.getServer().getName());
        var address = target.getAddress();
        if (address != null && address.getAddress() != null) {
            punishment.setIpAddress(address.getAddress().getHostAddress());
        }

        plugin.getPunishmentManager().issuePunishment(punishment).thenAccept(issued -> {
            if (issued != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String message = switch (selectedType) {
                        case WARN -> MessageUtil.getMessage("punishment.staff-warned");
                        case MUTE -> MessageUtil.getMessage("punishment.staff-muted");
                        case KICK -> MessageUtil.getMessage("punishment.staff-kicked");
                        case BAN -> MessageUtil.getMessage("punishment.staff-banned");
                        case BUILDBAN -> MessageUtil.getMessage("punishment.staff-buildbanned"); // NEW
                    };
                    message = message.replace("{player}", issued.getPlayerName())
                            .replace("{reason}", selectedReason)
                            .replace("{duration}",
                                    selectedDuration == 0 ? "Permanent" : TimeUtil.formatDuration(selectedDuration));
                    MessageUtil.sendMessage(staff, message);
                });
            } else {
                Bukkit.getScheduler().runTask(plugin,
                        () -> MessageUtil.sendMessage(staff, MessageUtil.getMessage("punishment.already-punished")));
            }
        });
    }

    public void open() {
        GuiRegistry.register(staff, inventory, this);
        @SuppressWarnings("nullness")
        var opened = staff.openInventory(inventory);
    }

    public Inventory getInventory() {
        return inventory;
    }
}
