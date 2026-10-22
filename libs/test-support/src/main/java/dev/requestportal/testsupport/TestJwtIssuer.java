package dev.requestportal.testsupport;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Stands in for Keycloak in tests: publishes a signing key at the realm's JWKS path and issues access
 * tokens shaped like the real ones (audience, {@code roles}, {@code department}, name claims).
 *
 * <p>Point a service at it with {@code KEYCLOAK_URL = issuer.baseUrl()} and
 * {@code KEYCLOAK_ISSUER_URI = TestJwtIssuer.ISSUER}.
 */
public final class TestJwtIssuer implements AutoCloseable {

    public static final String ISSUER = "http://keycloak.test/realms/request-portal";
    public static final String JWKS_PATH = "/realms/request-portal/protocol/openid-connect/certs";

    private final RSAKey signingKey;
    private final HttpServer server;

    private TestJwtIssuer(RSAKey signingKey, HttpServer server) {
        this.signingKey = signingKey;
        this.server = server;
    }

    public static TestJwtIssuer start() {
        RSAKey key = newKey("test-key");
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext(JWKS_PATH, exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(jwks);
                }
            });
            server.start();
            return new TestJwtIssuer(key, server);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** A fresh RSA key, e.g. to sign a token the services must reject. */
    public static RSAKey newKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Base URL to use as {@code KEYCLOAK_URL}. */
    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** A token for a new random user with the given roles. */
    public TokenBuilder token(String... roles) {
        return new TokenBuilder().roles(roles);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public final class TokenBuilder {

        private final JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(UUID.randomUUID().toString())
                .audience("request-portal-api")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(900)))
                .claim("preferred_username", "user")
                .claim("email", "user@example.edu")
                .claim("family_name", "Nguyễn")
                .claim("given_name", "Văn Test");
        private RSAKey key = signingKey;

        private TokenBuilder() {
        }

        public TokenBuilder subject(UUID id) {
            claims.subject(id.toString());
            return this;
        }

        public TokenBuilder username(String username) {
            claims.claim("preferred_username", username);
            return this;
        }

        public TokenBuilder email(String email) {
            claims.claim("email", email);
            return this;
        }

        /** Vietnamese order: family name first, as in "Phạm" + "Thị Dung". */
        public TokenBuilder name(String familyName, String givenName) {
            claims.claim("family_name", familyName).claim("given_name", givenName);
            return this;
        }

        public TokenBuilder roles(String... roles) {
            claims.claim("roles", List.of(roles));
            return this;
        }

        public TokenBuilder department(String departmentCode) {
            claims.claim("department", departmentCode);
            return this;
        }

        public TokenBuilder issuer(String issuer) {
            claims.issuer(issuer);
            return this;
        }

        public TokenBuilder audience(String audience) {
            claims.audience(audience);
            return this;
        }

        public TokenBuilder expired() {
            claims.issueTime(Date.from(Instant.now().minusSeconds(3600)))
                    .expirationTime(Date.from(Instant.now().minusSeconds(1800)));
            return this;
        }

        public TokenBuilder signedWith(RSAKey otherKey) {
            this.key = otherKey;
            return this;
        }

        public String build() {
            try {
                SignedJWT jwt = new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
                jwt.sign(new RSASSASigner(key));
                return jwt.serialize();
            } catch (JOSEException exception) {
                throw new IllegalStateException(exception);
            }
        }

        /** The value for an {@code Authorization} header. */
        public String bearer() {
            return "Bearer " + build();
        }
    }
}
