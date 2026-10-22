package dev.requestportal.request.template;

import java.time.Instant;

/**
 * @param totalSlaDays sum of the steps' deadlines: the longest a request should take
 */
public record TemplateSummary(
        Long id,
        String title,
        String description,
        String departmentCode,
        Audience audience,
        TemplateStatus status,
        int fieldCount,
        int stepCount,
        int totalSlaDays,
        Instant updatedAt) {

    static TemplateSummary from(RequestTemplate template) {
        return new TemplateSummary(
                template.getId(),
                template.getTitle(),
                template.getDescription(),
                template.getDepartmentCode(),
                template.getAudience(),
                template.getStatus(),
                template.getFields().size(),
                template.getSteps().size(),
                template.getSteps().stream().mapToInt(TemplateStep::getSlaDays).sum(),
                template.getUpdatedAt());
    }
}
