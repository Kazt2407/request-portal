package dev.requestportal.document.bulk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "bulk_download")
public class BulkDownload {

    @Id
    private UUID id;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "request_ids", nullable = false, updatable = false)
    private List<Long> requestIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BulkDownloadStatus status;

    @Column(name = "file_count")
    private Integer fileCount;

    @Column(name = "total_bytes")
    private Long totalBytes;

    @Column(name = "object_key", length = 300)
    private String objectKey;

    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected BulkDownload() {
    }

    static BulkDownload pending(UUID requestedBy, List<Long> requestIds, Instant now) {
        BulkDownload download = new BulkDownload();
        download.id = UUID.randomUUID();
        download.requestedBy = requestedBy;
        download.requestIds = new ArrayList<>(requestIds);
        download.status = BulkDownloadStatus.PENDING;
        download.createdAt = now;
        return download;
    }

    void started(Instant now) {
        status = BulkDownloadStatus.RUNNING;
        startedAt = now;
    }

    void ready(String key, int files, long bytes, Duration ttl, Instant now) {
        status = BulkDownloadStatus.READY;
        objectKey = key;
        fileCount = files;
        totalBytes = bytes;
        finishedAt = now;
        expiresAt = now.plus(ttl);
    }

    void failed(String message, Instant now) {
        status = BulkDownloadStatus.FAILED;
        error = message;
        finishedAt = now;
    }

    void expired() {
        status = BulkDownloadStatus.EXPIRED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public List<Long> getRequestIds() {
        return List.copyOf(requestIds);
    }

    public BulkDownloadStatus getStatus() {
        return status;
    }

    public Integer getFileCount() {
        return fileCount;
    }

    public Long getTotalBytes() {
        return totalBytes;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
