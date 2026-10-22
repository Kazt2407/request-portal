package dev.requestportal.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class NotificationApiTests extends NotificationTestSupport {

    @Test
    void adminsListNotificationsOfARequestAndRetryFailedOnes() throws Exception {
        Request request = newRequest("Retry test");
        publish("REQUEST_SUBMITTED", request, Map.of("stepCount", 1));
        await().atMost(Duration.ofSeconds(30)).until(() -> "SENT".equals(status(request.id())));
        jdbc.sql("update notification set status = 'FAILED', sent_at = null, attempts = 5, last_error = 'SMTP down'"
                        + " where request_id = ?")
                .param(request.id())
                .update();
        long id = jdbc.sql("select id from notification where request_id = ?").param(request.id()).query(Long.class).single();

        var list = mvc.get().uri("/api/v1/notifications?requestId=" + request.id())
                .header(AUTHORIZATION, admin())
                .exchange();
        assertThat(list).hasStatusOk();
        String body = list.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<List<String>>read(body, "$.items[*].status")).containsExactly("FAILED");
        assertThat(JsonPath.<List<String>>read(body, "$.items[*].lastError")).containsExactly("SMTP down");

        var retried = mvc.post().uri("/api/v1/notifications/{id}/retry", id).header(AUTHORIZATION, admin()).exchange();
        assertThat(retried).hasStatusOk();
        await().atMost(Duration.ofSeconds(30)).until(() -> "SENT".equals(status(request.id())));

        assertThat(mvc.post().uri("/api/v1/notifications/{id}/retry", id).header(AUTHORIZATION, admin()))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void onlyAdminsSeeNotifications() {
        assertThat(mvc.get().uri("/api/v1/notifications")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/notifications")
                .header(AUTHORIZATION, token("STAFF").department("ACAD").bearer()))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    private String admin() {
        return token("ADMIN").bearer();
    }

    private String status(long requestId) {
        return jdbc.sql("select status from notification where request_id = ?")
                .param(requestId)
                .query(String.class)
                .optional()
                .orElse(null);
    }
}
