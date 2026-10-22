package dev.requestportal.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AccountApiTests extends ApiTestSupport {

    @Test
    void meCreatesTheAccountFromTokenClaims() throws Exception {
        var result = mvc.get().uri("/api/v1/accounts/me").header(AUTHORIZATION, bearer("staff.finance")).exchange();

        assertThat(result).hasStatusOk();
        var body = json(result);
        assertThat(body.read("$.fullName", String.class)).isEqualTo("Phạm Thị Dung");
        assertThat(body.read("$.email", String.class)).isEqualTo("dung.pham@example.edu");
        assertThat(body.<List<String>>read("$.roles")).containsExactly("STAFF");
        assertThat(body.read("$.department.code", String.class)).isEqualTo("FIN");
        assertThat(body.read("$.lastSeenAt", String.class)).isNotNull();
    }

    @Test
    void meWithoutTokenIsUnauthorized() {
        assertThat(mvc.get().uri("/api/v1/accounts/me")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenForAnotherAudienceOrGarbageIsRejected() {
        assertThat(mvc.get().uri("/api/v1/accounts/me").header(AUTHORIZATION, "Bearer not-a-jwt"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void searchIsAdminOnly() {
        assertThat(mvc.get().uri("/api/v1/accounts").header(AUTHORIZATION, bearer("student")))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void searchIgnoresVietnameseAccentsAndFiltersByRole() throws Exception {
        signIn("student");
        signIn("staff.finance");
        signIn("staff.academic");
        String admin = bearer("admin");

        var byName = mvc.get().uri("/api/v1/accounts?q=nguyen").header(AUTHORIZATION, admin).exchange();
        assertThat(byName).hasStatusOk();
        assertThat(json(byName).<List<String>>read("$.items[*].fullName")).containsExactly("Nguyễn Văn An");

        var accented = mvc.get().uri("/api/v1/accounts?q=Phạm").header(AUTHORIZATION, admin).exchange();
        assertThat(json(accented).<List<String>>read("$.items[*].fullName")).containsExactly("Phạm Thị Dung");

        var staff = mvc.get().uri("/api/v1/accounts?role=STAFF&sort=fullName").header(AUTHORIZATION, admin).exchange();
        assertThat(json(staff).<List<String>>read("$.items[*].fullName"))
                .containsExactly("Lê Văn Cường", "Phạm Thị Dung");
        assertThat(json(staff).read("$.totalItems", Integer.class)).isEqualTo(2);
    }

    @Test
    void updateAccessChangesKeycloakAndTheLocalCopy() throws Exception {
        String lecturerId = signIn("lecturer");
        Integer academicId = departmentId("ACAD");
        String admin = bearer("admin");

        var updated = mvc.put().uri("/api/v1/accounts/{id}/access", lecturerId)
                .header(AUTHORIZATION, admin)
                .contentType(APPLICATION_JSON)
                .content("""
                        {"roles": ["LECTURER", "STAFF"], "departmentId": %d}
                        """.formatted(academicId))
                .exchange();

        assertThat(updated).hasStatusOk();
        assertThat(json(updated).<List<String>>read("$.roles")).containsExactly("LECTURER", "STAFF");
        assertThat(json(updated).read("$.department.code", String.class)).isEqualTo("ACAD");

        var newToken = claims(accessToken("lecturer"));
        assertThat(newToken.<List<String>>read("$.roles")).containsExactlyInAnyOrder("LECTURER", "STAFF");
        assertThat(newToken.read("$.department", String.class)).isEqualTo("ACAD");

        // Restore, which also checks that removing a role and the department works.
        assertThat(mvc.put().uri("/api/v1/accounts/{id}/access", lecturerId)
                .header(AUTHORIZATION, admin)
                .contentType(APPLICATION_JSON)
                .content("""
                        {"roles": ["LECTURER"], "departmentId": null}
                        """))
                .hasStatusOk();
        var restoredToken = claims(accessToken("lecturer"));
        assertThat(restoredToken.<List<String>>read("$.roles")).containsExactly("LECTURER");
        assertThat(restoredToken.read("$.department", String.class)).isNull();
    }

    @Test
    void staffWithoutDepartmentIsRejectedBeforeTouchingKeycloak() throws Exception {
        String studentId = signIn("student");

        var result = mvc.put().uri("/api/v1/accounts/{id}/access", studentId)
                .header(AUTHORIZATION, bearer("admin"))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"roles": ["STAFF"]}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(json(result).read("$.detail", String.class)).contains("must belong to a department");
        assertThat(claims(accessToken("student")).<List<String>>read("$.roles")).containsExactly("STUDENT");
    }

    @Test
    void adminCannotRemoveTheirOwnAdminRole() throws Exception {
        String adminId = signIn("admin");

        assertThat(mvc.put().uri("/api/v1/accounts/{id}/access", adminId)
                .header(AUTHORIZATION, bearer("admin"))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"roles": ["STAFF"], "departmentId": %d}
                        """.formatted(departmentId("IT"))))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void emptyRolesAreInvalid() throws Exception {
        String studentId = signIn("student");

        var result = mvc.put().uri("/api/v1/accounts/{id}/access", studentId)
                .header(AUTHORIZATION, bearer("admin"))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"roles": []}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(json(result).read("$.errors.roles", String.class)).isNotBlank();
    }

    /** Calls /me as the user so their account exists, and returns its id. */
    private String signIn(String username) throws Exception {
        var result = mvc.get().uri("/api/v1/accounts/me").header(AUTHORIZATION, bearer(username)).exchange();
        assertThat(result).hasStatusOk();
        return json(result).read("$.id");
    }

    private Integer departmentId(String code) throws Exception {
        var result = mvc.get().uri("/api/v1/departments").header(AUTHORIZATION, bearer("admin")).exchange();
        List<Integer> ids = json(result).read("$[?(@.code == '" + code + "')].id");
        return ids.getFirst();
    }
}
