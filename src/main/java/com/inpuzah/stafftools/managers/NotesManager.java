package com.inpuzah.stafftools.managers;

import com.inpuzah.stafftools.StaffToolsPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class NotesManager {

    private final StaffToolsPlugin plugin;

    public NotesManager(StaffToolsPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Integer> addNote(UUID playerUuid, String playerName, UUID staffUuid,
                                              String staffName, String note) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = """
                    INSERT INTO staff_notes (player_uuid, player_name, staff_uuid, staff_name, note, timestamp)
                    VALUES (?, ?, ?, ?, ?, ?)
                """;

                try (PreparedStatement stmt = conn.prepareStatement(query, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, playerName);
                    stmt.setString(3, staffUuid.toString());
                    stmt.setString(4, staffName);
                    stmt.setString(5, note);
                    stmt.setLong(6, System.currentTimeMillis());

                    stmt.executeUpdate();

                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            int id = rs.getInt(1);

                            // Log to audit
                            plugin.getAuditManager().logAction(
                                    staffUuid,
                                    staffName,
                                    "NOTE_ADD",
                                    playerUuid,
                                    playerName,
                                    "Note: " + note
                            );

                            return id;
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to add note: " + e.getMessage());
            }
            return -1;
        });
    }

    public CompletableFuture<List<StaffNote>> getNotes(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<StaffNote> notes = new ArrayList<>();

            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = "SELECT * FROM staff_notes WHERE player_uuid = ? AND removed = 0 ORDER BY timestamp DESC";

                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, playerUuid.toString());

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            notes.add(new StaffNote(
                                    rs.getInt("id"),
                                    UUID.fromString(rs.getString("player_uuid")),
                                    rs.getString("player_name"),
                                    UUID.fromString(rs.getString("staff_uuid")),
                                    rs.getString("staff_name"),
                                    rs.getString("note"),
                                    rs.getLong("timestamp")
                            ));
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get notes: " + e.getMessage());
            }

            return notes;
        });
    }

    public CompletableFuture<Boolean> removeNote(int noteId, UUID staffUuid, String staffName) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String query = "UPDATE staff_notes SET removed = 1 WHERE id = ?";

                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setInt(1, noteId);
                    int affected = stmt.executeUpdate();

                    if (affected > 0) {
                        // Log to audit
                        plugin.getAuditManager().logAction(
                                staffUuid,
                                staffName,
                                "NOTE_REMOVE",
                                null,
                                null,
                                "Removed note ID: " + noteId
                        );

                        return true;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to remove note: " + e.getMessage());
            }
            return false;
        });
    }

    public static class StaffNote {
        private final int id;
        private final UUID playerUuid;
        private final String playerName;
        private final UUID staffUuid;
        private final String staffName;
        private final String note;
        private final long timestamp;

        public StaffNote(int id, UUID playerUuid, String playerName, UUID staffUuid,
                         String staffName, String note, long timestamp) {
            this.id = id;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.staffUuid = staffUuid;
            this.staffName = staffName;
            this.note = note;
            this.timestamp = timestamp;
        }

        public int getId() { return id; }
        public UUID getPlayerUuid() { return playerUuid; }
        public String getPlayerName() { return playerName; }
        public UUID getStaffUuid() { return staffUuid; }
        public String getStaffName() { return staffName; }
        public String getNote() { return note; }
        public long getTimestamp() { return timestamp; }
    }
}