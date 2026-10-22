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
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Runs the gateway against a stand-in Keycloak that signs tokens and a stub org-service that records
 * what the gateway forwards, so no real Keycloak is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayApplicationTests {

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
