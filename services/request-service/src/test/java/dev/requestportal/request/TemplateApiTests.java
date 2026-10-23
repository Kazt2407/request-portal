package dev.requestportal.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TemplateApiTests extends ApiTestSupport {

    @Test
    void adminCreatesTemplateWithOrderedFieldsAndSteps() throws Exception {
        String title = uniqueTitle("Leave of absence");
        var result = mvc.post().uri("/api/v1/templates")
                .header(AUTHORIZATION, bearer(ADMIN))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"title": "%s", "departmentCode": "STU", "audience": "ALL", "status": "ACTIVE",
                         "showProcessingTime": true,
                         "fields": [
                           {"key": "from_date", "label": "From", "type": "TEXT", "required": true},
                           {"key": "kind", "label": "Kind", "type": "SELECT", "options": [" Sick ", "Family"]}
                         ],
                         "steps": [
                           {"departmentCode": "STU", "description": "Approve", "slaDays": 3},
                           {"departmentCode": "ACAD", "description": "Update records", "slaDays": 1, "requiresAttachment": true}
                         ]}
                        """.formatted(title))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        var body = json(result);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/v1/templates/" + body.read("$.id"));
        assertThat(strings(body, "$.fields[*].key")).containsExactly("from_date", "kind");
        assertThat(strings(body, "$.fields[1].options")).containsExactly("Sick", "Family");
        assertThat(body.<List<Integer>>read("$.steps[*].position")).containsExactly(1, 2);
        assertThat(body.read("$.steps[1].requiresAttachment", Boolean.class)).isTrue();
        assertThat(body.read("$.version", Integer.class)).isZero();
    }

    @Test
    void requestersSeeOnlyActiveTemplatesForTheirAudience() throws Exception {
        long forStudents = createTemplate(simpleTemplate(uniqueTitle("For students"), "STUDENT", "ACTIVE"));
        long forLecturers = createTemplate(simpleTemplate(uniqueTitle("For lecturers"), "LECTURER", "ACTIVE"));
        long inactive = createTemplate(simpleTemplate(uniqueTitle("Retired"), "STUDENT", "INACTIVE"));

        var studentView = json(mvc.get().uri("/api/v1/templates/available").header(AUTHORIZATION, bearer(AN)).exchange());
        assertThat(studentView.<List<Integer>>read("$[*].id"))
                .contains((int) forStudents)
                .doesNotContain((int) forLecturers, (int) inactive);

        var lecturerView = json(mvc.get().uri("/api/v1/templates/available")
                .header(AUTHORIZATION, bearer(LECTURER)).exchange());
        assertThat(lecturerView.<List<Integer>>read("$[*].id")).contains((int) forLecturers).doesNotContain((int) forStudents);

        assertThat(mvc.get().uri("/api/v1/templates/{id}", inactive).header(AUTHORIZATION, bearer(AN)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/v1/templates/{id}", inactive).header(AUTHORIZATION, bearer(ADMIN)))
                .hasStatusOk();
    }

    @Test
    void rulesBeyondBeanValidationAreReportedPerField() throws Exception {
        var result = mvc.post().uri("/api/v1/templates")
                .header(AUTHORIZATION, bearer(ADMIN))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"title": "Broken", "departmentCode": "NOPE", "audience": "STUDENT", "status": "ACTIVE",
                         "fields": [
                           {"key": "a", "label": "A", "type": "TEXT"},
                           {"key": "a", "label": "A again", "type": "TEXT"},
                           {"key": "choice", "label": "Choice", "type": "SELECT"}
                         ],
                         "steps": [{"departmentCode": "LIBRARY", "description": "Check", "slaDays": 1}]}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        var errors = json(result);
        assertThat(errors.read("$.errors['fields[1].key']", String.class)).isEqualTo("duplicates fields[0].key");
        assertThat(errors.read("$.errors['fields[2].options']", String.class)).isEqualTo("must list at least one option");
        assertThat(errors.read("$.errors.departmentCode", String.class)).isEqualTo("is not a known department");
        assertThat(errors.read("$.errors['steps[0].departmentCode']", String.class)).isEqualTo("is not a known department");
    }

    @Test
    void beanValidationCoversShapeOfTheDefinition() throws Exception {
        var result = mvc.post().uri("/api/v1/templates")
                .header(AUTHORIZATION, bearer(ADMIN))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"title": "", "departmentCode": "ACAD", "audience": "STUDENT", "status": "ACTIVE",
                         "fields": [{"key": "Bad Key", "label": "X", "type": "TEXT"}],
                         "steps": []}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        var errors = json(result);
        assertThat(errors.read("$.errors.title", String.class)).isNotBlank();
        assertThat(errors.read("$.errors.steps", String.class)).isNotBlank();
        assertThat(errors.read("$.errors['fields[0].key']", String.class)).startsWith("must be lower_snake_case");
    }

    @Test
    void updateReplacesTheDefinitionWithOptimisticLocking() throws Exception {
        String title = uniqueTitle("Card replacement");
        long id = createTemplate(simpleTemplate(title, "STUDENT", "ACTIVE"));
        String update = """
                {"title": "%s", "departmentCode": "STU", "audience": "STUDENT", "status": "INACTIVE",
                 "fields": [{"key": "card_number", "label": "Card number", "type": "TEXT", "required": true}],
                 "steps": [
                   {"departmentCode": "STU", "description": "Print a new card", "slaDays": 2},
                   {"departmentCode": "FIN", "description": "Charge the fee", "slaDays": 1}
                 ],
                 "version": %d}
                """;

        var updated = mvc.put().uri("/api/v1/templates/{id}", id)
                .header(AUTHORIZATION, bearer(ADMIN))
                .contentType(APPLICATION_JSON)
                .content(update.formatted(title, 0))
                .exchange();
        assertThat(updated).hasStatusOk();
        var body = json(updated);
        assertThat(body.read("$.version", Integer.class)).isEqualTo(1);
        assertThat(body.read("$.status", String.class)).isEqualTo("INACTIVE");
        assertThat(strings(body, "$.fields[*].key")).containsExactly("card_number");
        assertThat(strings(body, "$.steps[*].departmentCode")).containsExactly("STU", "FIN");

        assertThat(mvc.put().uri("/api/v1/templates/{id}", id)
                .header(AUTHORIZATION, bearer(ADMIN))
                .contentType(APPLICATION_JSON)
                .content(update.formatted(title, 0)))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void onlyAdminsManageTemplatesAndOnlyStaffListThemAll() {
        assertThat(mvc.post().uri("/api/v1/templates")
                .header(AUTHORIZATION, bearer(ACAD_STAFF))
                .contentType(APPLICATION_JSON)
                .content(simpleTemplate("Sneaky", "STUDENT", "ACTIVE")))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/templates").header(AUTHORIZATION, bearer(AN)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/templates").header(AUTHORIZATION, bearer(ACAD_STAFF))).hasStatusOk();
    }

    private static String simpleTemplate(String title, String audience, String status) {
        return """
                {"title": "%s", "departmentCode": "ACAD", "audience": "%s", "status": "%s",
                 "fields": [{"key": "note", "label": "Note", "type": "TEXT"}],
                 "steps": [{"departmentCode": "ACAD", "description": "Handle", "slaDays": 1}]}
                """.formatted(title, audience, status);
    }
}
