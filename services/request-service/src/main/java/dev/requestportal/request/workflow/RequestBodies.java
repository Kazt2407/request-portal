package dev.requestportal.request.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JSON request bodies of the request endpoints. */
public final class RequestBodies {

    private RequestBodies() {
    }

    /** @param values field key to value: text, or a list of file ids for FILE and IMAGE fields */
    public record Submit(@NotNull Long templateId, @NotNull Map<String, Object> values) {
    }

    public record CompleteStep(@Size(max = 2000) String note, @Size(max = 10) List<@NotNull UUID> attachmentIds) {

        List<UUID> attachmentIdsOrEmpty() {
            return attachmentIds == null ? List.of() : attachmentIds;
        }
    }

    public record RejectStep(@NotBlank @Size(max = 2000) String reason) {
    }

    public record BulkComplete(@NotEmpty @Size(max = 100) List<@NotNull Long> requestIds, @Size(max = 2000) String note) {
    }

    public record BulkReject(@NotEmpty @Size(max = 100) List<@NotNull Long> requestIds,
            @NotBlank @Size(max = 2000) String reason) {
    }

    public record ResultReceived(@NotNull Boolean received) {
    }

    public record BulkResultReceived(@NotEmpty @Size(max = 100) List<@NotNull Long> requestIds,
            @NotNull Boolean received) {
    }

    /** @param codes codes of the requests that were changed */
    public record BulkResult(int count, List<String> codes) {
    }
}
