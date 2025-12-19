package com.inpuzah.stafftools.database.models;

import java.util.UUID;

public class Punishment {

    private int id;
    private UUID playerUuid;
    private String playerName;
    private UUID staffUuid;
    private String staffName;
    private PunishmentType type;
    private String reason;
    private long duration;
    private long timestamp;
    private Long expiresAt;
    private boolean active;
    private UUID removedBy;
    private Long removedAt;
    private String removedReason;
    private String serverName;
    private String ipAddress;

    public Punishment(UUID playerUuid, String playerName, UUID staffUuid, String staffName,
                      PunishmentType type, String reason, long duration) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.staffUuid = staffUuid;
        this.staffName = staffName;
        this.type = type;
        this.reason = reason;
        this.duration = duration;
        this.timestamp = System.currentTimeMillis();
        this.active = true;

        if (duration > 0) {
            this.expiresAt = timestamp + (duration * 60 * 1000);
        }
    }

    public Punishment(int id, UUID playerUuid, String playerName, UUID staffUuid, String staffName,
                      PunishmentType type, String reason, long duration, long timestamp,
                      Long expiresAt, boolean active, UUID removedBy, Long removedAt,
                      String removedReason, String serverName, String ipAddress) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.staffUuid = staffUuid;
        this.staffName = staffName;
        this.type = type;
        this.reason = reason;
        this.duration = duration;
        this.timestamp = timestamp;
        this.expiresAt = expiresAt;
        this.active = active;
        this.removedBy = removedBy;
        this.removedAt = removedAt;
        this.removedReason = removedReason;
        this.serverName = serverName;
        this.ipAddress = ipAddress;
    }

    public boolean isExpired() {
        if (expiresAt == null || duration == 0) {
            return false;
        }
        return System.currentTimeMillis() > expiresAt;
    }

    public boolean isPermanent() {
        return duration == 0;
    }

    public long getRemainingTime() {
        if (isPermanent() || expiresAt == null) {
            return -1;
        }
        long remaining = expiresAt - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public UUID getStaffUuid() { return staffUuid; }
    public String getStaffName() { return staffName; }
    public PunishmentType getType() { return type; }
    public String getReason() { return reason; }
    public long getDuration() { return duration; }
    public long getTimestamp() { return timestamp; }
    public Long getExpiresAt() { return expiresAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public UUID getRemovedBy() { return removedBy; }
    public void setRemovedBy(UUID removedBy) { this.removedBy = removedBy; }
    public Long getRemovedAt() { return removedAt; }
    public void setRemovedAt(Long removedAt) { this.removedAt = removedAt; }
    public String getRemovedReason() { return removedReason; }
    public void setRemovedReason(String removedReason) { this.removedReason = removedReason; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}