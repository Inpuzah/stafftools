package com.inpuzah.stafftools;

import com.inpuzah.stafftools.commands.*;
import com.inpuzah.stafftools.managers.DatabaseManager;
import com.inpuzah.stafftools.listeners.*;
import com.inpuzah.stafftools.managers.*;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class StaffToolsPlugin extends JavaPlugin {

    private static StaffToolsPlugin instance;

    // Managers
    private com.inpuzah.stafftools.debug.DebugManager debugManager;
    private DatabaseManager databaseManager;
    private PunishmentManager punishmentManager;
    private StaffModeManager staffModeManager;
    private VanishManager vanishManager;
    private FreezeManager freezeManager;
    private AuditManager auditManager;
    private NotesManager notesManager;
    private ReportManager reportManager;
    private AppealManager appealManager;
    private BuildBanManager buildBanManager;
    private DiscordManager discordManager;
    private com.inpuzah.stafftools.chat.ChatPromptManager chatPromptManager;

    // Services
    private LuckPerms luckPerms;

    @Override
    public void onEnable() {
        instance = this;

        // Config
        saveDefaultConfig();

        // LuckPerms
        if (!setupLuckPerms()) {
            getLogger().severe("LuckPerms not found! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Database
        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
            getLogger().info("Database initialized successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Managers
        debugManager = new com.inpuzah.stafftools.debug.DebugManager(this);
        auditManager = new AuditManager(this);
        punishmentManager = new PunishmentManager(this);
        staffModeManager = new StaffModeManager(this);
        vanishManager = new VanishManager(this);
        freezeManager = new FreezeManager(this);
        notesManager = new NotesManager(this);
        reportManager = new ReportManager(this);
        appealManager = new AppealManager(this);
        buildBanManager = new BuildBanManager(this);
        discordManager = new DiscordManager(this);
        chatPromptManager = new com.inpuzah.stafftools.chat.ChatPromptManager(this);

        // Commands & Listeners
        registerCommands();
        registerListeners();

        // Optional: Plan integration (safe via reflection)
        try {
            Plugin plan = getServer().getPluginManager().getPlugin("Plan");
            if (plan != null && plan.isEnabled()) {
                Class<?> clazz = Class.forName("com.inpuzah.stafftools.managers.PlanIntegration");
                java.lang.reflect.Method register = clazz.getMethod("register",
                        com.inpuzah.stafftools.StaffToolsPlugin.class);
                register.invoke(null, this);
                getLogger().info("Plan integration registered.");
            }
        } catch (ClassNotFoundException ignored) {
            getLogger().warning("Plan detected but integration class not present; skipping.");
        } catch (Throwable t) {
            getLogger().warning("Plan integration failed: " + t.getMessage());
        }

    }

    @Override
    public void onDisable() {
        // Disable staff mode for all staff
        if (staffModeManager != null) {
            try {
                staffModeManager.disableAllStaffMode();
            } catch (Exception ignored) {
            }
        }

        // Close database
        if (databaseManager != null) {
            try {
                databaseManager.close();
            } catch (Exception ignored) {
            }
        }

        getLogger().info("StaffTools has been disabled!");
    }

    private boolean setupLuckPerms() {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
            return true;
        }
        return false;
    }

    private void registerCommands() {
        // Use null-safety in case a command isn’t in plugin.yml
        var cmd = getCommand("stafftools");
        if (cmd != null)
            cmd.setExecutor(new StaffToolsCommand(this));

        cmd = getCommand("punish");
        if (cmd != null)
            cmd.setExecutor(new PunishCommand(this));
        cmd = getCommand("warn");
        if (cmd != null)
            cmd.setExecutor(new WarnCommand(this));
        cmd = getCommand("mute");
        if (cmd != null)
            cmd.setExecutor(new MuteCommand(this));
        cmd = getCommand("unmute");
        if (cmd != null)
            cmd.setExecutor(new UnmuteCommand(this));
        cmd = getCommand("kick");
        if (cmd != null)
            cmd.setExecutor(new KickCommand(this));
        cmd = getCommand("ban");
        if (cmd != null)
            cmd.setExecutor(new BanCommand(this));
        cmd = getCommand("unban");
        if (cmd != null)
            cmd.setExecutor(new UnbanCommand(this));
        cmd = getCommand("punishhistory");
        if (cmd != null)
            cmd.setExecutor(new PunishHistoryCommand(this));
        cmd = getCommand("punishments");
        if (cmd != null)
            cmd.setExecutor(new PunishmentsCommand(this));

        cmd = getCommand("buildban");
        if (cmd != null)
            cmd.setExecutor(new BuildBanCommand(this));
        cmd = getCommand("unbuildban");
        if (cmd != null)
            cmd.setExecutor(new UnBuildBanCommand(this));

        cmd = getCommand("staffmode");
        if (cmd != null)
            cmd.setExecutor(new StaffModeCommand(this));
        cmd = getCommand("vanish");
        if (cmd != null)
            cmd.setExecutor(new VanishCommand(this));
        cmd = getCommand("freeze");
        if (cmd != null)
            cmd.setExecutor(new FreezeCommand(this));
        cmd = getCommand("invsee");
        if (cmd != null)
            cmd.setExecutor(new InvseeCommand());
        cmd = getCommand("notes");
        if (cmd != null)
            cmd.setExecutor(new NotesCommand(this));

        cmd = getCommand("report");
        if (cmd != null)
            cmd.setExecutor(new ReportCommand(this));
        cmd = getCommand("reports");
        if (cmd != null)
            cmd.setExecutor(new ReportsCommand(this));

        cmd = getCommand("appeal");
        if (cmd != null)
            cmd.setExecutor(new AppealCommand(this));
        cmd = getCommand("appeals");
        if (cmd != null)
            cmd.setExecutor(new AppealsCommand(this));

        cmd = getCommand("template");
        if (cmd != null)
            cmd.setExecutor(new TemplateCommand(this));
        cmd = getCommand("staffdashboard");
        if (cmd != null)
            cmd.setExecutor(new StaffDashboardCommand(this));

        cmd = getCommand("announce");
        if (cmd != null)
            cmd.setExecutor(new AnnounceCommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerChatListener(this), this);
        getServer().getPluginManager().registerEvents(new StaffModeListener(this), this);
        getServer().getPluginManager().registerEvents(new VanishListener(this), this);
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(), this);
        getServer().getPluginManager().registerEvents(new BuildBanListener(this), this);
    }

    /**
     * Reload config and refresh managers that depend on config values.
     *
     * Note: some settings (like command override priority) still require a server
     * restart.
     */
    public void reloadStaffTools() {
        reloadConfig();

        // Managers with scheduled tasks / runtime integration
        if (debugManager != null) {
            try {
                debugManager.reload();
            } catch (Exception e) {
                getLogger().warning("Failed to reload DebugManager: " + e.getMessage());
            }
        }
        if (vanishManager != null) {
            try {
                vanishManager.reload();
            } catch (Exception e) {
                getLogger().warning("Failed to reload VanishManager: " + e.getMessage());
            }
        }
        if (discordManager != null) {
            try {
                discordManager.reload();
            } catch (Exception e) {
                getLogger().warning("Failed to reload DiscordManager: " + e.getMessage());
            }
        }
    }

    // ----- Getters -----
    public static StaffToolsPlugin getInstance() {
        return instance;
    }

    public com.inpuzah.stafftools.debug.DebugManager getDebugManager() {
        return debugManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public StaffModeManager getStaffModeManager() {
        return staffModeManager;
    }

    public VanishManager getVanishManager() {
        return vanishManager;
    }

    public FreezeManager getFreezeManager() {
        return freezeManager;
    }

    public AuditManager getAuditManager() {
        return auditManager;
    }

    public NotesManager getNotesManager() {
        return notesManager;
    }

    public ReportManager getReportManager() {
        return reportManager;
    }

    public AppealManager getAppealManager() {
        return appealManager;
    }

    public BuildBanManager getBuildBanManager() {
        return buildBanManager;
    }

    public DiscordManager getDiscordManager() {
        return discordManager;
    }

    public com.inpuzah.stafftools.chat.ChatPromptManager getChatPromptManager() {
        return chatPromptManager;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }
}
