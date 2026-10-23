package dev.requestportal.document.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JSON shapes of the files API. */
public final class FileViews {

    private FileViews() {
    }

    /** @param size declared size in bytes; checked against the uploaded object */
    public record NewUpload(
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Size(max = 150) String contentType,
            @NotNull @Positive Long size,
            @NotNull FilePurpose purpose) {
    }

    /** Send the file with {@code method} to {@code uploadUrl}, with these headers, before {@code expiresAt}. */
    public record UploadTicket(UUID fileId, String uploadUrl, String method, Map<String, String> headers,
            Instant expiresAt) {
    }

    public record FileInfo(UUID id, String fileName, String contentType, long size, FilePurpose purpose,
            FileStatus status, Long requestId, Instant createdAt) {

        static FileInfo of(StoredFile file) {
            return new FileInfo(file.getId(), file.getFileName(), file.getContentType(), file.getSizeBytes(),
                    file.getPurpose(), file.getStatus(), file.getRequestId(), file.getCreatedAt());
        }
    }

    public record DownloadLink(String url, Instant expiresAt) {
    }

    public record CheckRequest(@NotEmpty @Size(max = 50) List<@NotNull UUID> fileIds) {
    }

    /** @param problem why the caller cannot use the file in a submission, or {@code null} if they can */
    public record FileCheck(UUID id, String fileName, String contentType, String problem) {
    }
}
