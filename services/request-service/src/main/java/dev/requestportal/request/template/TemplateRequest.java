package dev.requestportal.request.template;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The full definition of a template, used to create it and to replace it on update.
 *
 * @param departmentCode owning department, which prefixes request codes
 * @param version        the version the client last read; required on update for optimistic locking
 */
public record TemplateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotBlank @Size(max = 16) String departmentCode,
        @NotNull Audience audience,
        @NotNull TemplateStatus status,
        boolean showProcessingTime,
        @NotNull @Size(max = 50) List<@Valid @NotNull FieldDefinition> fields,
        @NotEmpty @Size(max = 20) List<@Valid @NotNull StepDefinition> steps,
        Long version) {

    public record FieldDefinition(
            @NotBlank
            @Pattern(regexp = "^[a-z][a-z0-9_]{0,49}$", message = "must be lower_snake_case, starting with a letter")
            String key,
            @NotBlank @Size(max = 200) String label,
            @Size(max = 2000) String description,
            @Size(max = 200) String hint,
            @NotNull FieldType type,
            boolean required,
            @Size(max = 100) List<@NotBlank @Size(max = 200) String> options,
            @Size(max = 120) String uploadFolder,
            @Size(max = 2000) String uploadNote) {

        public List<String> optionsOrEmpty() {
            return options == null ? List.of() : options;
        }
    }

    public record StepDefinition(
            @NotBlank @Size(max = 16) String departmentCode,
            @NotBlank @Size(max = 500) String description,
            @NotNull @Min(1) @Max(365) Integer slaDays,
            boolean requiresAttachment) {
    }
}
