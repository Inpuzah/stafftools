package com.inpuzah.stafftools.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiRegistry {
    private static final Map<UUID, Object> OPEN = new ConcurrentHashMap<>(); // viewer -> GUI object
    private static final Map<Inventory, Object> BY_INV = new ConcurrentHashMap<>();

    private GuiRegistry() {}

    public static void register(Player viewer, Inventory inv, Object gui) {
        OPEN.put(viewer.getUniqueId(), gui);
        BY_INV.put(inv, gui);
    }

    public static void unregister(Player viewer, Inventory inv) {
        if (viewer != null) OPEN.remove(viewer.getUniqueId());
        if (inv != null) BY_INV.remove(inv);
    }

    public static Object get(Inventory inv) { return BY_INV.get(inv); }
    public static Object get(Player viewer) { return OPEN.get(viewer.getUniqueId()); }
}
