package com.inpuzah.stafftools.database.models;

import java.util.UUID;

public class Report {
    private int id;
    private UUID reporterUuid;
    private String reporterName;
    private UUID reportedUuid;
    private String reportedName;
    private String reason;
    private long timestamp;
    private ReportStatus status;
    private UUID handledBy;
    private String handledByName;
    private Long handledAt;
    private String handlerNote;
    private String serverName;

    public Report(UUID reporterUuid, String reporterName, UUID reportedUuid,
                  String reportedName, String reason) {
        this.reporterUuid = reporterUuid;
        this.reporterName = reporterName;
        this.reportedUuid = reportedUuid;
        this.reportedName = reportedName;
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
        this.status = ReportStatus.OPEN;
    }

    public Report(int id, UUID reporterUuid, String reporterName, UUID reportedUuid,
                  String reportedName, String reason, long timestamp, ReportStatus status,
                  UUID handledBy, String handledByName, Long handledAt, String handlerNote,
                  String serverName) {
        this.id = id;
        this.reporterUuid = reporterUuid;
        this.reporterName = reporterName;
        this.reportedUuid = reportedUuid;
        this.reportedName = reportedName;
        this.reason = reason;
        this.timestamp = timestamp;
        this.status = status;
        this.handledBy = handledBy;
        this.handledByName = handledByName;
        this.handledAt = handledAt;
        this.handlerNote = handlerNote;
        this.serverName = serverName;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public UUID getReporterUuid() { return reporterUuid; }
    public String getReporterName() { return reporterName; }
    public UUID getReportedUuid() { return reportedUuid; }
    public String getReportedName() { return reportedName; }
    public String getReason() { return reason; }
    public long getTimestamp() { return timestamp; }
    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }
    public UUID getHandledBy() { return handledBy; }
    public void setHandledBy(UUID handledBy) { this.handledBy = handledBy; }
    public String getHandledByName() { return handledByName; }
    public void setHandledByName(String handledByName) { this.handledByName = handledByName; }
    public Long getHandledAt() { return handledAt; }
    public void setHandledAt(Long handledAt) { this.handledAt = handledAt; }
    public String getHandlerNote() { return handlerNote; }
    public void setHandlerNote(String handlerNote) { this.handlerNote = handlerNote; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
}