package dev.requestportal.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationServiceApplicationTests extends NotificationTestSupport {

    @Test
    void healthIsPublicAndUp() throws Exception {
        var result = mvc.get().uri("/actuator/health").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"UP\"");
    }
}
