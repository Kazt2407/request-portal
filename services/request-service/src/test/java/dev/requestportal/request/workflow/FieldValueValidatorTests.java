package dev.requestportal.request.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.requestportal.request.file.FileDirectory;
import dev.requestportal.request.org.OrgDirectory;
import dev.requestportal.request.template.Audience;
import dev.requestportal.request.template.FieldType;
import dev.requestportal.request.template.RequestTemplate;
import dev.requestportal.request.template.TemplateRequest;
import dev.requestportal.request.template.TemplateStatus;
import dev.requestportal.web.InvalidFieldsException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FieldValueValidatorTests {

    private final FieldValueValidator validator = new FieldValueValidator(new OrgDirectory() {
        @Override
        public Set<String> unknownDepartments(Collection<String> codes) {
            return Set.of();
        }

        @Override
        public boolean termExists(String termCode) {
            return termCode.equals("2026-2027-1");
        }

        @Override
        public boolean schoolYearExists(String schoolYear) {
            return schoolYear.equals("2026-2027");
        }
    }, ids -> ids.stream().collect(Collectors.toMap(Function.identity(),
            id -> new FileDirectory.FileCheck(id, "photo.png", "image/png", null))));

    private final RequestTemplate template = new RequestTemplate(new TemplateRequest(
            "Everything", null, "ACAD", Audience.ALL, TemplateStatus.ACTIVE, false,
            List.of(
                    field("title", FieldType.TEXT, true),
                    field("story", FieldType.TEXTAREA, false),
                    new TemplateRequest.FieldDefinition("size", "Size", null, null, FieldType.SELECT, false,
                            List.of("S", "M"), null, null),
                    field("term", FieldType.TERM, false),
                    field("year", FieldType.SCHOOL_YEAR, false),
                    field("photos", FieldType.IMAGE, false)),
            List.of(new TemplateRequest.StepDefinition("ACAD", "Handle", 1, false)),
            null), Instant.now());

    @Test
    void acceptsValidValuesAndSkipsEmptyOptionalOnes() {
        String photo = UUID.randomUUID().toString();
        Map<String, Object> input = new HashMap<>();
        input.put("title", " Hello ");
        input.put("story", "");
        input.put("size", "M");
        input.put("term", "2026-2027-1");
        input.put("year", null);
        input.put("photos", List.of(photo));

        List<SubmittedValue> values = validator.validate(template, input);

        assertThat(values).extracting(value -> value.field().getKey()).containsExactly("title", "size", "term", "photos");
        assertThat(values.getFirst().value()).containsExactly("Hello");
        assertThat(values.getLast().value()).containsExactly(photo);
    }

    @Test
    void reportsEveryProblemAtOnce() {
        Map<String, Object> input = Map.of(
                "story", "x".repeat(FieldValueValidator.MAX_TEXTAREA_LENGTH + 1),
                "size", "XL",
                "term", 20261,
                "year", "1999-2000",
                "photos", List.of("a", "b"),
                "unknown", "?");

        InvalidFieldsException error = catchThrowableOfType(
                InvalidFieldsException.class, () -> validator.validate(template, input));

        assertThat(error.getErrors()).containsOnlyKeys("title", "story", "size", "term", "year", "photos", "unknown");
        assertThat(error.getErrors().get("title")).isEqualTo("is required");
        assertThat(error.getErrors().get("term")).startsWith("must be text");
    }

    @Test
    void singleValuedFieldsRejectLists() {
        InvalidFieldsException error = catchThrowableOfType(
                InvalidFieldsException.class, () -> validator.validate(template, Map.of("title", List.of("a", "b"))));

        assertThat(error.getErrors()).containsEntry("title", "must be a single value");
    }

    private static TemplateRequest.FieldDefinition field(String key, FieldType type, boolean required) {
        return new TemplateRequest.FieldDefinition(key, key, null, null, type, required, null, null, null);
    }
}
