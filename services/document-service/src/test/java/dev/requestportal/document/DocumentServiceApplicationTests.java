package dev.requestportal.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DocumentServiceApplicationTests extends DocumentTestSupport {

    @Test
    void healthIsPublicAndUp() throws Exception {
        var result = mvc.get().uri("/actuator/health").exchange();

        assertThat(result).hasStatusOk();
        assertThat(json(result).read("$.status", String.class)).isEqualTo("UP");
    }

    @Test
    void filesRequireAToken() {
        assertThat(mvc.post().uri("/api/v1/files")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
