package com.inpuzah.stafftools.listeners;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Punishment;
import com.inpuzah.stafftools.utils.MessageUtil;
import com.inpuzah.stafftools.utils.TimeUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.InventoryHolder;

public class BuildBanListener implements Listener {

    private final StaffToolsPlugin plugin;

    public BuildBanListener(StaffToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (plugin.getBuildBanManager().isBuildBanned(player.getUniqueId())) {
            event.setCancelled(true);
            sendBuildBanMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (plugin.getBuildBanManager().isBuildBanned(player.getUniqueId())) {
            event.setCancelled(true);
            sendBuildBanMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Cancel certain interactions while buildbanned
        if (plugin.getBuildBanManager().isBuildBanned(player.getUniqueId())) {
            switch (event.getAction()) {
                case RIGHT_CLICK_BLOCK:
                    // Block opening containers (chests, barrels, shulkers, etc.)
                    if (event.getClickedBlock() != null && isContainer(event.getClickedBlock())) {
                        event.setCancelled(true);
                        sendBuildBanMessage(player);
                    }
                    // Block placing blocks with items in hand
                    if (event.hasItem() && event.getItem().getType().isBlock()) {
                        event.setCancelled(true);
                        sendBuildBanMessage(player);
                    }
                    break;
                case LEFT_CLICK_BLOCK:
                    // Also prevent left-click interactions (redstone, buttons, etc.)
                    if (event.getClickedBlock() != null && (isContainer(event.getClickedBlock())
                            || isRedstoneBlock(event.getClickedBlock().getType()))) {
                        event.setCancelled(true);
                        sendBuildBanMessage(player);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private boolean isContainer(org.bukkit.block.Block block) {
        if (block == null)
            return false;
        BlockState state = block.getState();
        return state instanceof InventoryHolder;
    }

    private boolean isRedstoneBlock(org.bukkit.Material material) {
        return material == org.bukkit.Material.LEVER ||
                material == org.bukkit.Material.REDSTONE_WIRE ||
                material == org.bukkit.Material.REDSTONE_TORCH ||
                material == org.bukkit.Material.REPEATER ||
                material == org.bukkit.Material.COMPARATOR ||
                material == org.bukkit.Material.DAYLIGHT_DETECTOR ||
                material == org.bukkit.Material.HOPPER ||
                material == org.bukkit.Material.DISPENSER ||
                material == org.bukkit.Material.DROPPER ||
                material == org.bukkit.Material.PISTON ||
                material == org.bukkit.Material.STICKY_PISTON ||
                material == org.bukkit.Material.STONE_BUTTON ||
                material == org.bukkit.Material.POLISHED_BLACKSTONE_BUTTON ||
                material == org.bukkit.Material.NOTE_BLOCK ||
                material == org.bukkit.Material.BELL;
    }

    private void sendBuildBanMessage(Player player) {
        Punishment buildban = plugin.getBuildBanManager().getActiveBuildBan(player.getUniqueId());
        if (buildban == null)
            return;

        java.util.List<String> messages = plugin.getConfig().getStringList("buildban.build-attempt-message");

        String expiresText;
        if (buildban.isPermanent()) {
            expiresText = "Never (Permanent)";
        } else {
            expiresText = TimeUtil.formatDate(buildban.getExpiresAt()) + " (" +
                    TimeUtil.formatDurationMillis(buildban.getRemainingTime()) + " remaining)";
        }

        for (String line : messages) {
            String formatted = line
                    .replace("{reason}", buildban.getReason())
                    .replace("{duration}", expiresText);
            player.sendMessage(MessageUtil.colorize(formatted));
        }
    }
}