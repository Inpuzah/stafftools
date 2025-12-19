package com.inpuzah.stafftools.managers;

import com.djrapitops.plan.capability.CapabilityService;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.ExtensionService;
import com.djrapitops.plan.extension.annotation.NumberProvider;
import com.djrapitops.plan.extension.annotation.PluginInfo;
import com.djrapitops.plan.extension.annotation.StringProvider;
import com.djrapitops.plan.extension.annotation.BooleanProvider;
import com.djrapitops.plan.extension.annotation.TabInfo;
import com.djrapitops.plan.extension.annotation.TabOrder;
import com.djrapitops.plan.extension.icon.Color;
import com.djrapitops.plan.extension.icon.Family;
import com.inpuzah.stafftools.StaffToolsPlugin;
import com.inpuzah.stafftools.database.models.Punishment;
import com.inpuzah.stafftools.database.models.PunishmentType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@PluginInfo(name = "StaffTools", iconName = "shield-alt", iconFamily = Family.SOLID, color = Color.RED)
@TabInfo(tab = "Punishments", iconName = "gavel", elementOrder = {})
@TabOrder({ "Punishments" })
public final class PlanIntegration implements DataExtension {

    private final StaffToolsPlugin plugin;

    public PlanIntegration(StaffToolsPlugin plugin) {
        this.plugin = plugin;
    }

    public static void register(StaffToolsPlugin plugin) {
        try {
            // Check if Plan is available and has required capability
            CapabilityService capabilities = CapabilityService.getInstance();
            if (!capabilities.hasCapability("DATA_EXTENSION_VALUES")) {
                plugin.getLogger()
                        .warning("Plan does not support DataExtension API. Update Plan to enable integration.");
                return;
            }

            // Register the extension
            PlanIntegration extension = new PlanIntegration(plugin);
            ExtensionService.getInstance().register(extension);
            plugin.getLogger().info("Plan integration registered successfully.");

        } catch (NoClassDefFoundError e) {
            plugin.getLogger().warning("Plan is not installed. Skipping Plan integration.");
        } catch (IllegalStateException e) {
            plugin.getLogger().warning("Plan is not enabled yet. Skipping Plan integration.");
        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe("Plan DataExtension implementation error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register Plan integration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @NumberProvider(text = "Total Punishments", description = "Total number of punishments received", priority = 100, iconName = "list", iconFamily = Family.SOLID, iconColor = Color.GREY)
    public long totalPunishments(UUID playerUUID) {
        try {
            return plugin.getPunishmentManager().getPlayerHistory(playerUUID).get().size();
        } catch (InterruptedException | ExecutionException e) {
            return 0;
        }
    }

    @NumberProvider(text = "Active Punishments", description = "Number of currently active punishments", priority = 99, iconName = "exclamation-triangle", iconFamily = Family.SOLID, iconColor = Color.RED)
    public long activePunishments(UUID playerUUID) {
        try {
            return plugin.getPunishmentManager().getPlayerHistory(playerUUID).get().stream()
                    .filter(Punishment::isActive)
                    .count();
        } catch (InterruptedException | ExecutionException e) {
            return 0;
        }
    }

    @NumberProvider(text = "Warnings", description = "Total warnings received", priority = 90, iconName = "exclamation", iconFamily = Family.SOLID, iconColor = Color.AMBER)
    public long totalWarnings(UUID playerUUID) {
        try {
            return plugin.getPunishmentManager().getPlayerHistory(playerUUID).get().stream()
                    .filter(p -> p.getType() == PunishmentType.WARN)
                    .count();
        } catch (InterruptedException | ExecutionException e) {
            return 0;
        }
    }

    @NumberProvider(text = "Mutes", description = "Total mutes received", priority = 80, iconName = "volume-mute", iconFamily = Family.SOLID, iconColor = Color.ORANGE)
    public long totalMutes(UUID playerUUID) {
        try {
            return plugin.getPunishmentManager().getPlayerHistory(playerUUID).get().stream()
                    .filter(p -> p.getType() == PunishmentType.MUTE)
                    .count();
        } catch (InterruptedException | ExecutionException e) {
            return 0;
        }
    }

    @NumberProvider(text = "Kicks", description = "Total kicks received", priority = 70, iconName = "door-open", iconFamily = Family.SOLID, iconColor = Color.BLUE)
    public long totalKicks(UUID playerUUID) {
        try {
            return plugin.getPunishmentManager().getPlayerHistory(playerUUID).get().stream()
                    .filter(p -> p.getType() == PunishmentType.KICK)
                    .count();
        } catch (InterruptedException | ExecutionException e) {
            return 0;
        }
    }

    @NumberProvider(text = "Bans", description = "Total bans received", priority = 60, iconName = "ban", iconFamily = Family.SOLID, iconColor = Color.RED)
    public long totalBans(UUID playerUUID) {
        try {
            return plugin.getPunishmentManager().getPlayerHistory(playerUUID).get().stream()
                    .filter(p -> p.getType() == PunishmentType.BAN)
                    .count();
        } catch (InterruptedException | ExecutionException e) {
            return 0;
        }
    }

    @NumberProvider(text = "Build Bans", description = "Total build bans received", priority = 50, iconName = "hammer", iconFamily = Family.SOLID, iconColor = Color.BROWN)
    public long totalBuildBans(UUID playerUUID) {
        try {
            return plugin.getPunishmentManager().getPlayerHistory(playerUUID).get().stream()
                    .filter(p -> p.getType() == PunishmentType.BUILDBAN)
                    .count();
        } catch (InterruptedException | ExecutionException e) {
            return 0;
        }
    }

    @BooleanProvider(text = "Is Build Banned", description = "Whether the player is currently build banned", priority = 40, iconName = "lock", iconFamily = Family.SOLID, iconColor = Color.RED, conditionName = "buildBanned")
    public boolean isBuildBanned(UUID playerUUID) {
        return plugin.getBuildBanManager().isBuildBanned(playerUUID);
    }

    @StringProvider(text = "Last Punishment", description = "Most recent punishment reason", priority = 30, iconName = "history", iconFamily = Family.SOLID, iconColor = Color.GREY)
    public String lastPunishment(UUID playerUUID) {
        try {
            List<Punishment> history = plugin.getPunishmentManager().getPlayerHistory(playerUUID).get();
            if (history.isEmpty()) {
                return "None";
            }

            Punishment latest = history.get(history.size() - 1);
            return latest.getType().name() + ": " + latest.getReason();
        } catch (InterruptedException | ExecutionException e) {
            return "Error loading";
        }
    }
}
