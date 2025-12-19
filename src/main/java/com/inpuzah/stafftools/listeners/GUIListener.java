package com.inpuzah.stafftools.listeners;

import com.inpuzah.stafftools.gui.GuiRegistry;
import com.inpuzah.stafftools.gui.PunishmentGUI;
import com.inpuzah.stafftools.gui.PunishmentHistoryGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

public class GUIListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;

        Object gui = GuiRegistry.get(e.getView().getTopInventory());
        if (gui == null) return; // not one of ours

        // Disallow taking/placing items inside the GUI
        if (clicked.equals(e.getView().getTopInventory())) {
            e.setCancelled(true);

            // Only react to left/right clicks on a slot
            if (e.getCurrentItem() != null && e.getSlot() >= 0 &&
                    (e.getClick() == ClickType.LEFT || e.getClick() == ClickType.RIGHT)) {

                int rawSlot = e.getRawSlot(); // slot in the top inventory
                if (gui instanceof PunishmentGUI pg) {
                    pg.handleClick(rawSlot);
                } else if (gui instanceof PunishmentHistoryGUI ph) {
                    ph.handleClick(rawSlot);
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Object gui = GuiRegistry.get(e.getView().getTopInventory());
        if (gui != null && e.getPlayer() instanceof Player p) {
            GuiRegistry.unregister(p, e.getView().getTopInventory());
        }
    }
}
