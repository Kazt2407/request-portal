package dev.requestportal.document.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FormViews {

    private FormViews() {
    }

    /** @param version required when updating, for optimistic locking */
    public record FormRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull FormAudience audience,
            boolean active,
            @NotNull UUID templateFileId,
            @NotNull @Size(max = 30) List<@Valid @NotNull FieldDefinition> fields,
            Long version) {
    }

    public record FieldDefinition(
            @NotBlank
            @Pattern(regexp = "^[a-z][a-z0-9_]{0,49}$", message = "must be lower_snake_case, starting with a letter")
            String key,
            @NotBlank @Size(max = 200) String label,
            @Size(max = 200) String hint,
            @NotNull FormFieldType type,
            boolean required) {
    }

    public record Form(Long id, String title, String description, FormAudience audience, boolean active,
            UUID templateFileId, List<String> placeholders, List<FieldDefinition> fields, Instant updatedAt,
            Long version) {

        static Form of(FormTemplate form) {
            return new Form(form.getId(), form.getTitle(), form.getDescription(), form.getAudience(), form.isActive(),
                    form.getTemplateFileId(), form.getPlaceholders(),
                    form.getFields().stream()
                            .map(field -> new FieldDefinition(field.getKey(), field.getLabel(), field.getHint(),
                                    field.getType(), field.isRequired()))
                            .toList(),
                    form.getUpdatedAt(), form.getVersion());
        }
    }

    /** @param values what the user typed, by field key */
    public record FillRequest(@NotNull Map<String, String> values) {
    }

    public record FilledDocument(String fileName, byte[] content) {
    }
}
