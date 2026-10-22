package dev.requestportal.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RequestServiceApplicationTests extends ApiTestSupport {

    @Test
    void healthIsPublicAndUpWithDatabaseConnected() throws Exception {
        var result = mvc.get().uri("/actuator/health").exchange();

        assertThat(result).hasStatusOk();
        assertThat(json(result).read("$.status", String.class)).isEqualTo("UP");
    }

    @Test
    void apiRequiresAToken() {
        assertThat(mvc.get().uri("/api/v1/templates/available")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void devDataProvidesSampleTemplates() throws Exception {
        var result = mvc.get().uri("/api/v1/templates/available")
                .header("Authorization", bearer(AN))
                .exchange();

        assertThat(strings(json(result), "$[*].title"))
                .contains("Student status confirmation", "Tuition fee receipt", "Official transcript")
                .doesNotContain("Teaching schedule change");
    }
}
