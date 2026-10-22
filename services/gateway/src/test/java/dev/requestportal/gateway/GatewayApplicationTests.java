package dev.requestportal.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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
 * Runs the gateway against a stub that plays both Keycloak (serving the signing keys) and org-service
 * (recording what the gateway forwards). Tokens are signed here, so no real Keycloak is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayApplicationTests {

    private static final String ISSUER = "http://keycloak.test/realms/request-portal";
    private static final RSAKey SIGNING_KEY = rsaKey("test-key");
    private static final AtomicReference<String> forwardedAuthorization = new AtomicReference<>();
    private static final HttpServer stub = startStub();

    @Value("${local.server.port}")
    int port;

    @Autowired
    RouteLocator routeLocator;

    WebTestClient client;

    @DynamicPropertySource
    static void pointAtStub(DynamicPropertyRegistry registry) {
        String stubUrl = "http://localhost:" + stub.getAddress().getPort();
        registry.add("KEYCLOAK_URL", () -> stubUrl);
        registry.add("KEYCLOAK_ISSUER_URI", () -> ISSUER);
        registry.add("ORG_SERVICE_URL", () -> stubUrl);
    }

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        forwardedAuthorization.set(null);
    }

    @AfterAll
    static void stopStub() {
        stub.stop(0);
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
        String token = token(SIGNING_KEY, claims -> {});

        client.get().uri("/api/v1/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        assertThat(forwardedAuthorization.get()).isEqualTo("Bearer " + token);
    }

    @Test
    void tokenForAnotherAudienceIsRejected() {
        assertRejected(token(SIGNING_KEY, claims -> claims.audience("some-other-api")));
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        assertRejected(token(SIGNING_KEY, claims -> claims.issuer("http://evil.test/realms/request-portal")));
    }

    @Test
    void expiredTokenIsRejected() {
        assertRejected(token(SIGNING_KEY, claims -> claims
                .issueTime(Date.from(Instant.now().minusSeconds(3600)))
                .expirationTime(Date.from(Instant.now().minusSeconds(1800)))));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        assertRejected(token(rsaKey("test-key"), claims -> {}));
    }

    private void assertRejected(String token) {
        client.get().uri("/api/v1/accounts/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
        assertThat(forwardedAuthorization.get()).isNull();
    }

    private static String token(RSAKey key, Consumer<JWTClaimsSet.Builder> customizer) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("ed6b922f-57e8-4957-8885-df72a9d1bfc5")
                .audience("request-portal-api")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("roles", List.of("STUDENT"));
        customizer.accept(claims);
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static RSAKey rsaKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/realms/request-portal/protocol/openid-connect/certs",
                    exchange -> respond(exchange, new JWKSet(SIGNING_KEY.toPublicJWK()).toString()));
            server.createContext("/api/", exchange -> {
                forwardedAuthorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                respond(exchange, "{\"ok\":true}");
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
