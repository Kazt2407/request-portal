package dev.requestportal.request.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.requestportal.web.UpstreamServiceException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Exercises the real HTTP client against a stub org-service. */
class OrgServiceClientTests {

    private final Map<String, String> responses = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-22T00:00:00Z"));
    private HttpServer server;
    private OrgServiceClient client;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            calls.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
            authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            String body = responses.get(path);
            byte[] bytes = (body == null ? "{}" : body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(body == null ? 500 : 200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        client = new OrgServiceClient("http://localhost:" + server.getAddress().getPort(), clock);

        Jwt jwt = Jwt.withTokenValue("user-token").header("alg", "none").subject("someone").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
        SecurityContextHolder.clearContext();
    }

    @Test
    void reportsUnknownDepartmentsUsingTheCallersToken() {
        responses.put("/api/v1/departments", "[{\"code\": \"ACAD\"}, {\"code\": \"FIN\"}]");

        assertThat(client.unknownDepartments(List.of("ACAD", "LIB", "FIN", "ZOO"))).containsExactly("LIB", "ZOO");
        assertThat(authorizations).containsExactly("Bearer user-token");
    }

    @Test
    void cachesTermsButReloadsOnceForAnUnknownValue() {
        responses.put("/api/v1/terms", "[{\"code\": \"2026-2027-1\"}]");

        assertThat(client.termExists("2026-2027-1")).isTrue();
        assertThat(client.termExists("2026-2027-1")).isTrue();
        assertThat(calls.get("/api/v1/terms")).hasValue(1);

        clock.advance(Duration.ofSeconds(30));
        responses.put("/api/v1/terms", "[{\"code\": \"2026-2027-1\"}, {\"code\": \"2026-2027-2\"}]");
        assertThat(client.termExists("2026-2027-2")).isTrue();
        assertThat(calls.get("/api/v1/terms")).hasValue(2);

        assertThat(client.termExists("1999-2000-1")).isFalse();
        assertThat(calls.get("/api/v1/terms")).as("no reload storm for unknown values").hasValue(2);
    }

    @Test
    void failuresBecomeUpstreamErrors() {
        assertThatThrownBy(() -> client.schoolYearExists("2026-2027"))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("/api/v1/terms/school-years");
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
