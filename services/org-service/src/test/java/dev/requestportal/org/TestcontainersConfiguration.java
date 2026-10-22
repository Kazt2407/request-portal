package dev.requestportal.org;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** PostgreSQL and a Keycloak loaded with the same realm file as docker compose. */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    private static final String REALM_FILE = "../../infra/keycloak/realm-request-portal.json";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.6-alpine"));
    }

    @Bean
    GenericContainer<?> keycloakContainer() {
        return new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.8.0"))
                .withCommand("start-dev", "--import-realm")
                .withEnv("KC_HEALTH_ENABLED", "true")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(REALM_FILE), "/opt/keycloak/data/import/realm-request-portal.json")
                .withExposedPorts(8080, 9000)
                .waitingFor(Wait.forHttp("/health/ready").forPort(9000))
                .withStartupTimeout(Duration.ofMinutes(3));
    }

    @Bean
    DynamicPropertyRegistrar keycloakProperties(GenericContainer<?> keycloakContainer) {
        return registry -> {
            registry.add("portal.keycloak.base-url", () -> keycloakUrl(keycloakContainer));
            registry.add("portal.keycloak.issuer-uri",
                    () -> keycloakUrl(keycloakContainer) + "/realms/request-portal");
        };
    }

    private static String keycloakUrl(GenericContainer<?> keycloak) {
        return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
    }
}
