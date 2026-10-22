package dev.requestportal.notification;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL, Kafka and Mailpit (a real SMTP server with an inbox API), as in docker compose. */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    static final class MailpitContainer extends GenericContainer<MailpitContainer> {

        MailpitContainer() {
            super(DockerImageName.parse("axllent/mailpit:v1.31.4"));
            withExposedPorts(1025, 8025);
            // Mailpit looks up the client's hostname before greeting it; inside Docker that can stall past the
            // SMTP read timeout, so the first email of a run would fail at random.
            withEnv("MP_SMTP_DISABLE_RDNS", "true");
            waitingFor(Wait.forHttp("/readyz").forPort(8025));
        }

        String apiUrl() {
            return "http://" + getHost() + ":" + getMappedPort(8025) + "/api/v1";
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
    MailpitContainer mailpitContainer() {
        return new MailpitContainer();
    }

    @Bean
    DynamicPropertyRegistrar mailProperties(MailpitContainer mailpit) {
        return registry -> {
            registry.add("spring.mail.host", mailpit::getHost);
            registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
        };
    }
}
