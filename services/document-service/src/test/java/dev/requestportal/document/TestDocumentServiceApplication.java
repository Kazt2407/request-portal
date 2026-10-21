package dev.requestportal.document;

import org.springframework.boot.SpringApplication;

/**
 * Runs the service locally against a throwaway PostgreSQL container: {@code ./mvnw -pl services/document-service spring-boot:test-run}.
 */
public class TestDocumentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(DocumentServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
