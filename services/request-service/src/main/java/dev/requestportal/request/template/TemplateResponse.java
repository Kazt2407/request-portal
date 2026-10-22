package dev.requestportal.request.template;

import java.time.Instant;
import java.util.List;

public record TemplateResponse(
        Long id,
        String title,
        String description,
        String departmentCode,
        Audience audience,
        TemplateStatus status,
        boolean showProcessingTime,
        List<Field> fields,
        List<Step> steps,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    public record Field(
            String key,
            String label,
            String description,
            String hint,
            FieldType type,
            boolean required,
            List<String> options,
            String uploadFolder,
            String uploadNote) {
    }

    public record Step(int position, String departmentCode, String description, int slaDays,
            boolean requiresAttachment) {
    }

    static TemplateResponse from(RequestTemplate template) {
        return new TemplateResponse(
                template.getId(),
                template.getTitle(),
                template.getDescription(),
                template.getDepartmentCode(),
                template.getAudience(),
                template.getStatus(),
                template.isShowProcessingTime(),
                template.getFields().stream()
                        .map(field -> new Field(
                                field.getKey(),
                                field.getLabel(),
                                field.getDescription(),
                                field.getHint(),
                                field.getType(),
                                field.isRequired(),
                                field.getOptions(),
                                field.getUploadFolder(),
                                field.getUploadNote()))
                        .toList(),
                template.getSteps().stream()
                        .map(step -> new Step(
                                step.getPosition(),
                                step.getDepartmentCode(),
                                step.getDescription(),
                                step.getSlaDays(),
                                step.isRequiresAttachment()))
                        .toList(),
                template.getCreatedAt(),
                template.getUpdatedAt(),
                template.getVersion());
    }
}
