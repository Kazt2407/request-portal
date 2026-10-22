package dev.requestportal.org;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrgServiceApplicationTests extends ApiTestSupport {

    @Test
    void healthIsPublicAndUpWithDatabaseConnected() throws Exception {
        var result = mvc.get().uri("/actuator/health").exchange();

        assertThat(result).hasStatusOk();
        assertThat(json(result).read("$.status", String.class)).isEqualTo("UP");
    }
}
