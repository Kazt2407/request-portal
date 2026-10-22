package dev.requestportal.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DepartmentApiTests extends ApiTestSupport {

    @Test
    void listRequiresAuthentication() {
        assertThat(mvc.get().uri("/api/v1/departments")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anySignedInUserCanListDepartments() throws Exception {
        var result = mvc.get().uri("/api/v1/departments").header(AUTHORIZATION, bearer("student")).exchange();

        assertThat(result).hasStatusOk();
        List<String> codes = json(result).read("$[*].code");
        assertThat(codes).contains("ACAD", "FIN", "IT", "STU").isSorted();
    }

    @Test
    void adminCreatesDepartment() throws Exception {
        var result = mvc.post().uri("/api/v1/departments")
                .header(AUTHORIZATION, bearer("admin"))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"code": "LIB", "name": "Library"}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        Integer id = json(result).read("$.id");
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/v1/departments/" + id);
        assertThat(json(result).read("$.name", String.class)).isEqualTo("Library");
    }

    @Test
    void duplicateCodeIsAConflict() {
        assertThat(mvc.post().uri("/api/v1/departments")
                .header(AUTHORIZATION, bearer("admin"))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"code": "ACAD", "name": "Another academic office"}
                        """))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void invalidFieldsAreReportedPerField() throws Exception {
        var result = mvc.post().uri("/api/v1/departments")
                .header(AUTHORIZATION, bearer("admin"))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"code": "lib", "name": " "}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(json(result).read("$.errors.code", String.class)).startsWith("must be 2-16 upper-case");
        assertThat(json(result).read("$.errors.name", String.class)).isNotBlank();
    }

    @Test
    void onlyAdminsCanCreateDepartments() {
        assertThat(mvc.post().uri("/api/v1/departments")
                .header(AUTHORIZATION, bearer("staff.academic"))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"code": "HR", "name": "Human Resources"}
                        """))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateWithStaleVersionIsRejected() throws Exception {
        String admin = bearer("admin");
        var created = mvc.post().uri("/api/v1/departments")
                .header(AUTHORIZATION, admin)
                .contentType(APPLICATION_JSON)
                .content("""
                        {"code": "QA", "name": "Quality Assurance"}
                        """)
                .exchange();
        Integer id = json(created).read("$.id");

        var renamed = mvc.put().uri("/api/v1/departments/{id}", id)
                .header(AUTHORIZATION, admin)
                .contentType(APPLICATION_JSON)
                .content("""
                        {"name": "Quality Assurance Office", "version": 0}
                        """)
                .exchange();
        assertThat(renamed).hasStatusOk();
        assertThat(json(renamed).read("$.version", Integer.class)).isEqualTo(1);

        assertThat(mvc.put().uri("/api/v1/departments/{id}", id)
                .header(AUTHORIZATION, admin)
                .contentType(APPLICATION_JSON)
                .content("""
                        {"name": "QA Office", "version": 0}
                        """))
                .hasStatus(HttpStatus.CONFLICT);
    }
}
