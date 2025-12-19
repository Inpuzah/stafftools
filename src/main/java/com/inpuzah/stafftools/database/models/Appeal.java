package com.inpuzah.stafftools.database.models;

import java.util.UUID;

public class Appeal {
    private int id;
    private int punishmentId;
    private UUID playerUuid;
    private String playerName;
    private String appealText;
    private long timestamp;
    private AppealStatus status;
    private UUID reviewedBy;
    private String reviewedByName;
    private Long reviewedAt;
    private String reviewNote;

    public Appeal(int punishmentId, UUID playerUuid, String playerName, String appealText) {
        this.punishmentId = punishmentId;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.appealText = appealText;
        this.timestamp = System.currentTimeMillis();
        this.status = AppealStatus.PENDING;
    }

    public Appeal(int id, int punishmentId, UUID playerUuid, String playerName,
                  String appealText, long timestamp, AppealStatus status,
                  UUID reviewedBy, String reviewedByName, Long reviewedAt, String reviewNote) {
        this.id = id;
        this.punishmentId = punishmentId;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.appealText = appealText;
        this.timestamp = timestamp;
        this.status = status;
        this.reviewedBy = reviewedBy;
        this.reviewedByName = reviewedByName;
        this.reviewedAt = reviewedAt;
        this.reviewNote = reviewNote;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getPunishmentId() { return punishmentId; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public String getAppealText() { return appealText; }
    public long getTimestamp() { return timestamp; }
    public AppealStatus getStatus() { return status; }
    public void setStatus(AppealStatus status) { this.status = status; }
    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewedByName() { return reviewedByName; }
    public void setReviewedByName(String name) { this.reviewedByName = name; }
    public Long getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Long reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewedNote(String reviewNote) { this.reviewNote = reviewNote; }
}