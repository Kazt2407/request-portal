package dev.requestportal.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AcademicTermApiTests extends ApiTestSupport {

    @Test
    void termsAreListedInDateOrderAndFilterableBySchoolYear() throws Exception {
        String student = bearer("student");

        var all = mvc.get().uri("/api/v1/terms").header(AUTHORIZATION, student).exchange();
        assertThat(all).hasStatusOk();
        assertThat(json(all).<List<String>>read("$[*].code"))
                .containsSubsequence("2025-2026-1", "2025-2026-2", "2025-2026-3", "2026-2027-1");

        var oneYear = mvc.get().uri("/api/v1/terms?schoolYear=2026-2027").header(AUTHORIZATION, student).exchange();
        assertThat(json(oneYear).<List<String>>read("$[*].code"))
                .containsExactly("2026-2027-1", "2026-2027-2", "2026-2027-3");
    }

    @Test
    void schoolYearsAreNewestFirst() throws Exception {
        var result = mvc.get().uri("/api/v1/terms/school-years").header(AUTHORIZATION, bearer("lecturer")).exchange();

        assertThat(result).hasStatusOk();
        assertThat(json(result).<List<String>>read("$")).startsWith("2026-2027", "2025-2026");
    }

    @Test
    void adminCreatesTermWithDerivedCode() throws Exception {
        var result = mvc.post().uri("/api/v1/terms")
                .header(AUTHORIZATION, bearer("admin"))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"schoolYear": "2027-2028", "termNumber": 1, "name": "Semester 1, 2027–2028",
                         "startsOn": "2027-09-06", "endsOn": "2028-01-16"}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(json(result).read("$.code", String.class)).isEqualTo("2027-2028-1");
    }

    @Test
    void overlappingDatesAreAConflict() throws Exception {
        var result = mvc.post().uri("/api/v1/terms")
                .header(AUTHORIZATION, bearer("admin"))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"schoolYear": "2024-2025", "termNumber": 3, "name": "Wrong dates",
                         "startsOn": "2025-08-01", "endsOn": "2025-09-30"}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(json(result).read("$.detail", String.class)).contains("Semester 1, 2025–2026");
    }

    @Test
    void endBeforeStartAndNonConsecutiveYearsAreInvalid() throws Exception {
        var result = mvc.post().uri("/api/v1/terms")
                .header(AUTHORIZATION, bearer("admin"))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"schoolYear": "2030-2032", "termNumber": 1, "name": "Bad",
                         "startsOn": "2030-09-01", "endsOn": "2030-08-01"}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(json(result).read("$.errors.endsOn", String.class)).isEqualTo("must be after startsOn");
        assertThat(json(result).read("$.errors.schoolYear", String.class)).contains("consecutive");
    }

    @Test
    void onlyAdminsCreateTerms() {
        assertThat(mvc.post().uri("/api/v1/terms")
                .header(AUTHORIZATION, bearer("staff.academic"))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"schoolYear": "2031-2032", "termNumber": 1, "name": "X",
                         "startsOn": "2031-09-01", "endsOn": "2032-01-01"}
                        """))
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}
