package dev.requestportal.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.requestportal.testsupport.TestJwtIssuer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the gateway against a stand-in Keycloak that signs tokens, a stub org-service that records what the
 * gateway forwards, and a real Redis for rate limiting.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GatewayApplicationTests.RedisConfiguration.class)
class GatewayApplicationTests {

    /** Low limits so the test can exceed them: one request per second, bursts of three. */
    private static final int BURST = 3;

    @TestConfiguration(proxyBeanMethods = false)
    static class RedisConfiguration {

        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redisContainer() {
            return new GenericContainer<>(DockerImageName.parse("redis:8.8.3-alpine")).withExposedPorts(6379);
        }
    }

    private static final TestJwtIssuer issuer = TestJwtIssuer.start();
    private static final AtomicReference<String> forwardedAuthorization = new AtomicReference<>();
    private static final HttpServer orgService = startOrgServiceStub();

    @Value("${local.server.port}")
    int port;

    @Autowired
    RouteLocator routeLocator;

    WebTestClient client;

    @DynamicPropertySource
    static void pointAtStubs(DynamicPropertyRegistry registry) {
        registry.add("KEYCLOAK_URL", issuer::baseUrl);
        registry.add("KEYCLOAK_ISSUER_URI", () -> TestJwtIssuer.ISSUER);
        registry.add("ORG_SERVICE_URL", () -> "http://localhost:" + orgService.getAddress().getPort());
        registry.add("RATE_LIMIT_PER_SECOND", () -> 1);
        registry.add("RATE_LIMIT_BURST", () -> BURST);
    }

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        forwardedAuthorization.set(null);
    }

    @AfterAll
    static void stopStubs() {
        orgService.stop(0);
        issuer.close();
    }

    @Test
    void everyServiceHasARoute() {
        List<String> routeIds = routeLocator.getRoutes().map(Route::getId).collectList().block();

        assertThat(routeIds).containsExactlyInAnyOrder(
                "org-service", "request-service", "document-service", "notification-service");
    }

    @Test
    void healthIsPublic() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void apiCallWithoutTokenIsRejected() {
        client.get().uri("/api/v1/accounts/me").exchange().expectStatus().isUnauthorized();
        assertThat(forwardedAuthorization.get()).isNull();
    }

    @Test
    void validTokenIsForwardedUnchanged() {
        String bearer = issuer.token("STUDENT").bearer();

        client.get().uri("/api/v1/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .exchange()
                .expectStatus().isOk();

        assertThat(forwardedAuthorization.get()).isEqualTo(bearer);
    }

    @Test
    void tokenForAnotherAudienceIsRejected() {
        assertRejected(issuer.token("STUDENT").audience("some-other-api").bearer());
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        assertRejected(issuer.token("STUDENT").issuer("http://evil.test/realms/request-portal").bearer());
    }

    @Test
    void expiredTokenIsRejected() {
        assertRejected(issuer.token("STUDENT").expired().bearer());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        assertRejected(issuer.token("STUDENT").signedWith(TestJwtIssuer.newKey("test-key")).bearer());
    }

    @Test
    void eachUserIsRateLimitedSeparately() {
        String busyUser = issuer.token("STUDENT").bearer();
        String otherUser = issuer.token("STUDENT").bearer();

        List<Integer> statuses = new java.util.ArrayList<>();
        for (int i = 0; i < BURST + 2; i++) {
            statuses.add(client.get().uri("/api/v1/accounts/me")
                    .header(HttpHeaders.AUTHORIZATION, busyUser)
                    .exchange()
                    .returnResult(String.class)
                    .getStatus()
                    .value());
        }

        assertThat(statuses).startsWith(200, 200, 200).contains(429);
        client.get().uri("/api/v1/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, otherUser)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-RateLimit-Remaining");
    }

    private void assertRejected(String bearer) {
        client.get().uri("/api/v1/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .exchange()
                .expectStatus().isUnauthorized();
        assertThat(forwardedAuthorization.get()).isNull();
    }

    private static HttpServer startOrgServiceStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/api/", exchange -> {
                forwardedAuthorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
