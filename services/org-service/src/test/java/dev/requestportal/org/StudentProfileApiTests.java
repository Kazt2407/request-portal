package dev.requestportal.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class StudentProfileApiTests extends ApiTestSupport {

    private static final String STUDENT_ID = "a1000000-0000-4000-8000-000000000001";
    private static final String LECTURER_ID = "a1000000-0000-4000-8000-000000000002";

    @Test
    void studentsReadTheirOwnProfile() throws Exception {
        var result = mvc.get().uri("/api/v1/profiles/me").header(AUTHORIZATION, bearer("student")).exchange();

        assertThat(result).hasStatusOk();
        assertThat(json(result).read("$.studentCode", String.class)).isEqualTo("21004321");
        assertThat(json(result).read("$.className", String.class)).isEqualTo("DHCNTT21A");
        assertThat(json(result).read("$.dateOfBirth", String.class)).isEqualTo("2003-05-14");
    }

    @Test
    void accountsWithoutAProfileGetNotFound() {
        assertThat(mvc.get().uri("/api/v1/profiles/me").header(AUTHORIZATION, bearer("staff.finance")))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void staffMaintainProfilesWithOptimisticLocking() throws Exception {
        String staff = bearer("staff.academic");
        var created = mvc.put().uri("/api/v1/profiles/{id}", LECTURER_ID)
                .header(AUTHORIZATION, staff)
                .contentType(APPLICATION_JSON)
                .content("""
                        {"studentCode": "gv00777", "className": "Teaching assistants"}
                        """)
                .exchange();
        assertThat(created).hasStatusOk();
        assertThat(json(created).read("$.studentCode", String.class)).isEqualTo("GV00777");

        assertThat(mvc.put().uri("/api/v1/profiles/{id}", LECTURER_ID)
                .header(AUTHORIZATION, staff)
                .contentType(APPLICATION_JSON)
                .content("""
                        {"studentCode": "GV00777", "version": 99}
                        """))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void studentCodesAreUniqueAndStudentsCannotEditProfiles() {
        assertThat(mvc.put().uri("/api/v1/profiles/{id}", LECTURER_ID)
                .header(AUTHORIZATION, bearer("admin"))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"studentCode": "21004321", "version": 0}
                        """))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.put().uri("/api/v1/profiles/{id}", STUDENT_ID)
                .header(AUTHORIZATION, bearer("student"))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"studentCode": "99999999"}
                        """))
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}
