package dev.requestportal.request;

import org.springframework.boot.SpringApplication;

/**
 * Runs the service locally against a throwaway PostgreSQL container:
 * {@code ./mvnw -pl services/request-service spring-boot:test-run}.
 */
public class TestRequestServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(RequestServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
