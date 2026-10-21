package dev.requestportal.notification;

import org.springframework.boot.SpringApplication;

/**
 * Runs the service locally against a throwaway PostgreSQL container: {@code ./mvnw -pl services/notification-service spring-boot:test-run}.
 */
public class TestNotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(NotificationServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
