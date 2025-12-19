package com.inpuzah.stafftools.managers;

import com.inpuzah.stafftools.StaffToolsPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class DatabaseManager {

    private final StaffToolsPlugin plugin;
    private HikariDataSource dataSource;
    private ExecutorService executor;
    private final String type;

    public DatabaseManager(StaffToolsPlugin plugin) {
        this.plugin = plugin;
        this.type = plugin.getConfig().getString("database.type", "sqlite");
    }

    public void initialize() throws SQLException {
        HikariConfig config = new HikariConfig();
        int configuredPoolSize = 3;

        if (type.equalsIgnoreCase("mysql")) {
            // ----- MySQL -----
            String host = plugin.getConfig().getString("database.mysql.host");
            int port = plugin.getConfig().getInt("database.mysql.port");
            String database = plugin.getConfig().getString("database.mysql.database");
            String username = plugin.getConfig().getString("database.mysql.username");
            String password = plugin.getConfig().getString("database.mysql.password");
            configuredPoolSize = Math.max(5, plugin.getConfig().getInt("database.mysql.pool-size", 10));

            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&autoReconnect=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8");
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(configuredPoolSize);
            config.setMinimumIdle(Math.min(2, configuredPoolSize));
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Timeouts
            config.setConnectionTimeout(10000);
            config.setIdleTimeout(60000);
            config.setMaxLifetime(600000);

        } else {
            // ----- SQLite (safe, single-writer, no 30s stalls) -----
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists())
                dataFolder.mkdirs();

            String fileName = plugin.getConfig().getString("database.sqlite.file", "stafftools.db");
            File dbFile = new File(dataFolder, fileName);

            // WAL + busy timeout + saner defaults
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath()
                    + "?journal_mode=WAL&busy_timeout=10000&synchronous=NORMAL&cache_size=10000");
            config.setDriverClassName("org.sqlite.JDBC");

            // SQLite: allow a few pooled connections to reduce 10s waits, WAL handles
            // readers
            configuredPoolSize = Math.max(2, plugin.getConfig().getInt("database.sqlite.pool-size", 3));
            config.setMaximumPoolSize(configuredPoolSize);
            config.setMinimumIdle(Math.min(1, configuredPoolSize));

            // Timeouts
            config.setConnectionTimeout(10000);
            config.setIdleTimeout(30000);
            config.setMaxLifetime(600000);
        }

        config.setPoolName("StaffTools-Pool");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        // Surface leaks quickly if a connection gets stuck
        config.setLeakDetectionThreshold(5000);

        dataSource = new HikariDataSource(config);

        // One-time pragmas for SQLite (no-op on MySQL)
        if (!isMySQL()) {
            try (Connection c = getConnection(); Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL;");
                s.execute("PRAGMA busy_timeout=10000;");
                s.execute("PRAGMA synchronous=NORMAL;");
                s.execute("PRAGMA foreign_keys=ON;");
            }
        }

        // Dedicated DB executor sized to pool; keeps CF work off common fork-join
        int threads = Math.max(2, Math.min(configuredPoolSize, 8));
        ThreadFactory tf = r -> {
            Thread t = new Thread(r);
            t.setName("stafftools-db-" + t.getId());
            t.setDaemon(true);
            return t;
        };
        executor = Executors.newFixedThreadPool(threads, tf);

        // Create tables
        createTables();
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection()) {
            String autoInc = type.equalsIgnoreCase("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT";

            // Punishments
            String punishmentsTable = """
                        CREATE TABLE IF NOT EXISTS punishments (
                            id INTEGER PRIMARY KEY %s,
                            player_uuid VARCHAR(36) NOT NULL,
                            player_name VARCHAR(16) NOT NULL,
                            staff_uuid VARCHAR(36) NOT NULL,
                            staff_name VARCHAR(16) NOT NULL,
                            type VARCHAR(20) NOT NULL,
                            reason TEXT NOT NULL,
                            duration BIGINT NOT NULL,
                            timestamp BIGINT NOT NULL,
                            expires_at BIGINT,
                            active BOOLEAN NOT NULL DEFAULT 1,
                            removed_by VARCHAR(36),
                            removed_at BIGINT,
                            removed_reason TEXT,
                            server_name VARCHAR(50),
                            ip_address VARCHAR(45)
                        )
                    """.formatted(autoInc);
            try (PreparedStatement stmt = conn.prepareStatement(punishmentsTable)) {
                stmt.execute();
            }

            // Appeals
            String appealsTable = """
                        CREATE TABLE IF NOT EXISTS punishment_appeals (
                            id INTEGER PRIMARY KEY %s,
                            punishment_id INTEGER NOT NULL,
                            player_uuid VARCHAR(36) NOT NULL,
                            appeal_text TEXT NOT NULL,
                            timestamp BIGINT NOT NULL,
                            status VARCHAR(20) NOT NULL,
                            reviewed_by VARCHAR(36),
                            reviewed_at BIGINT,
                            review_note TEXT,
                            FOREIGN KEY (punishment_id) REFERENCES punishments(id)
                        )
                    """.formatted(autoInc);
            try (PreparedStatement stmt = conn.prepareStatement(appealsTable)) {
                stmt.execute();
            }

            // Staff notes
            String notesTable = """
                        CREATE TABLE IF NOT EXISTS staff_notes (
                            id INTEGER PRIMARY KEY %s,
                            player_uuid VARCHAR(36) NOT NULL,
                            player_name VARCHAR(16) NOT NULL,
                            staff_uuid VARCHAR(36) NOT NULL,
                            staff_name VARCHAR(16) NOT NULL,
                            note TEXT NOT NULL,
                            timestamp BIGINT NOT NULL,
                            removed BOOLEAN NOT NULL DEFAULT 0
                        )
                    """.formatted(autoInc);
            try (PreparedStatement stmt = conn.prepareStatement(notesTable)) {
                stmt.execute();
            }

            // Audit
            String auditTable = """
                        CREATE TABLE IF NOT EXISTS audit_log (
                            id INTEGER PRIMARY KEY %s,
                            staff_uuid VARCHAR(36) NOT NULL,
                            staff_name VARCHAR(16) NOT NULL,
                            action VARCHAR(50) NOT NULL,
                            target_uuid VARCHAR(36),
                            target_name VARCHAR(16),
                            details TEXT,
                            timestamp BIGINT NOT NULL,
                            server_name VARCHAR(50)
                        )
                    """.formatted(autoInc);
            try (PreparedStatement stmt = conn.prepareStatement(auditTable)) {
                stmt.execute();
            }

            // Sessions
            String sessionsTable = """
                        CREATE TABLE IF NOT EXISTS player_sessions (
                            id INTEGER PRIMARY KEY %s,
                            player_uuid VARCHAR(36) NOT NULL,
                            player_name VARCHAR(16) NOT NULL,
                            ip_address VARCHAR(45) NOT NULL,
                            join_time BIGINT NOT NULL,
                            leave_time BIGINT,
                            server_name VARCHAR(50)
                        )
                    """.formatted(autoInc);
            try (PreparedStatement stmt = conn.prepareStatement(sessionsTable)) {
                stmt.execute();
            }

            // Reports
            String reportsTable = """
                        CREATE TABLE IF NOT EXISTS reports (
                            id INTEGER PRIMARY KEY %s,
                            reporter_uuid VARCHAR(36) NOT NULL,
                            reporter_name VARCHAR(16) NOT NULL,
                            reported_uuid VARCHAR(36) NOT NULL,
                            reported_name VARCHAR(16) NOT NULL,
                            reason TEXT NOT NULL,
                            timestamp BIGINT NOT NULL,
                            status VARCHAR(20) NOT NULL,
                            handled_by VARCHAR(36),
                            handled_by_name VARCHAR(16),
                            handled_at BIGINT,
                            handler_note TEXT,
                            server_name VARCHAR(50)
                        )
                    """.formatted(autoInc);
            try (PreparedStatement stmt = conn.prepareStatement(reportsTable)) {
                stmt.execute();
            }

            // BuildBan groups
            String buildBanGroupsTable = """
                        CREATE TABLE IF NOT EXISTS buildban_groups (
                            player_uuid VARCHAR(36) PRIMARY KEY,
                            original_group VARCHAR(50) NOT NULL,
                            needs_restoration BOOLEAN DEFAULT 0
                        )
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(buildBanGroupsTable)) {
                stmt.execute();
            }

            // Punishment templates
            String templatesTable = """
                        CREATE TABLE IF NOT EXISTS punishment_templates (
                            id INTEGER PRIMARY KEY %s,
                            name VARCHAR(50) UNIQUE NOT NULL,
                            type VARCHAR(20) NOT NULL,
                            reason TEXT NOT NULL,
                            duration BIGINT NOT NULL,
                            created_by VARCHAR(36) NOT NULL,
                            created_at BIGINT NOT NULL
                        )
                    """.formatted(autoInc);
            try (PreparedStatement stmt = conn.prepareStatement(templatesTable)) {
                stmt.execute();
            }

            // Staff statistics
            String statsTable = """
                        CREATE TABLE IF NOT EXISTS staff_statistics (
                            id INTEGER PRIMARY KEY %s,
                            staff_uuid VARCHAR(36) NOT NULL,
                            action_type VARCHAR(50) NOT NULL,
                            count INTEGER DEFAULT 1,
                            last_action BIGINT NOT NULL,
                            date_key VARCHAR(10) NOT NULL,
                            UNIQUE(staff_uuid, action_type, date_key)
                        )
                    """.formatted(autoInc);
            try (PreparedStatement stmt = conn.prepareStatement(statsTable)) {
                stmt.execute();
            }

            createIndexes(conn);
        }
    }

    private void createIndexes(Connection conn) throws SQLException {
        String[] indexes = {
                "CREATE INDEX IF NOT EXISTS idx_punishments_player ON punishments(player_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_punishments_active ON punishments(active)",
                "CREATE INDEX IF NOT EXISTS idx_punishments_type ON punishments(type)",
                "CREATE INDEX IF NOT EXISTS idx_punishments_timestamp ON punishments(timestamp)",
                "CREATE INDEX IF NOT EXISTS idx_appeals_punishment ON punishment_appeals(punishment_id)",
                "CREATE INDEX IF NOT EXISTS idx_appeals_player ON punishment_appeals(player_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_appeals_status ON punishment_appeals(status)",
                "CREATE INDEX IF NOT EXISTS idx_notes_player ON staff_notes(player_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_audit_staff ON audit_log(staff_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_log(action)",
                "CREATE INDEX IF NOT EXISTS idx_sessions_player ON player_sessions(player_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_sessions_ip ON player_sessions(ip_address)",
                "CREATE INDEX IF NOT EXISTS idx_reports_status ON reports(status)",
                "CREATE INDEX IF NOT EXISTS idx_reports_reported ON reports(reported_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_reports_timestamp ON reports(timestamp)",
                "CREATE INDEX IF NOT EXISTS idx_stats_staff ON staff_statistics(staff_uuid)",
                "CREATE INDEX IF NOT EXISTS idx_stats_date ON staff_statistics(date_key)"
        };
        for (String index : indexes) {
            try (PreparedStatement stmt = conn.prepareStatement(index)) {
                stmt.execute();
            }
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed())
            dataSource.close();
    }

    public boolean isMySQL() {
        return type.equalsIgnoreCase("mysql");
    }
}
