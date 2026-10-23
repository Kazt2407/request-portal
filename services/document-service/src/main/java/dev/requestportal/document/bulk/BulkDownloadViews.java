package dev.requestportal.document.bulk;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BulkDownloadViews {

    private BulkDownloadViews() {
    }

    public record Selection(@NotEmpty List<@NotNull Long> requestIds) {
    }

    /**
     * @param visibleRequests requests of the selection the caller may see; the others are left out
     * @param problem         why the archive cannot be built, or {@code null}
     */
    public record Preview(int requests, int visibleRequests, int files, long totalBytes, String problem) {
    }

    /** @param downloadUrl a pre-signed URL once the archive is ready */
    public record Job(UUID id, BulkDownloadStatus status, int requests, Integer files, Long totalBytes, String error,
            Instant createdAt, Instant finishedAt, Instant expiresAt, String downloadUrl) {
    }
}
