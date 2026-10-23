package dev.requestportal.document.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Entity
@Table(name = "stored_file")
public class StoredFile {

    private static final DateTimeFormatter KEY_MONTH = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private FilePurpose purpose;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    /** Never contains the user's file name, so names cannot collide or traverse paths. */
    @Column(name = "object_key", nullable = false, updatable = false, length = 300)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FileStatus status;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "attached_at")
    private Instant attachedAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    protected StoredFile() {
    }

    static StoredFile pending(
            UUID ownerId, FilePurpose purpose, String fileName, String contentType, long sizeBytes, Instant now) {
        StoredFile file = new StoredFile();
        file.id = UUID.randomUUID();
        file.ownerId = ownerId;
        file.purpose = purpose;
        file.fileName = fileName;
        file.contentType = contentType;
        file.sizeBytes = sizeBytes;
        file.objectKey = purpose.keyPrefix() + "/" + KEY_MONTH.format(now) + "/" + file.id;
        file.status = FileStatus.PENDING;
        file.createdAt = now;
        return file;
    }

    void uploaded(Instant now) {
        status = FileStatus.UPLOADED;
        uploadedAt = now;
    }

    /** Attaches the file to a request; repeating it for the same request is a no-op. */
    boolean attachTo(long request, Instant now) {
        if (status == FileStatus.ATTACHED) {
            return requestId != null && requestId == request;
        }
        if (status != FileStatus.UPLOADED) {
            return false;
        }
        status = FileStatus.ATTACHED;
        requestId = request;
        attachedAt = now;
        return true;
    }

    /** A file kept for something other than a request, such as a form template. */
    void keep(Instant now) {
        status = FileStatus.ATTACHED;
        attachedAt = now;
    }

    void checked(Instant now) {
        lastCheckedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public FilePurpose getPurpose() {
        return purpose;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public FileStatus getStatus() {
        return status;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
