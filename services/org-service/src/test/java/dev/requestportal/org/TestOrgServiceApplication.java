package dev.requestportal.org;

import org.springframework.boot.SpringApplication;

/**
 * Runs the service locally against a throwaway PostgreSQL container: {@code ./mvnw -pl services/org-service spring-boot:test-run}.
 */
public class TestOrgServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(OrgServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
