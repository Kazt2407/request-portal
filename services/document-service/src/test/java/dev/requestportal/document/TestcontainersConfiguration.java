package dev.requestportal.document;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL, Kafka and RustFS (S3-compatible storage), the same versions as docker compose. */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    static final class StorageContainer extends GenericContainer<StorageContainer> {

        StorageContainer() {
            super(DockerImageName.parse("rustfs/rustfs:1.0.1"));
            withEnv("RUSTFS_ACCESS_KEY", "portal");
            withEnv("RUSTFS_SECRET_KEY", "portal-storage-secret");
            withEnv("RUSTFS_VOLUMES", "/data");
            withExposedPorts(9000);
            waitingFor(Wait.forHttp("/health").forPort(9000));
        }

        String endpoint() {
            return "http://" + getHost() + ":" + getMappedPort(9000);
        }
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.6-alpine"));
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));
    }

    @Bean
    StorageContainer storageContainer() {
        return new StorageContainer();
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(StorageContainer storage) {
        return registry -> {
            registry.add("portal.storage.endpoint", storage::endpoint);
            registry.add("portal.storage.public-endpoint", storage::endpoint);
        };
    }
}
