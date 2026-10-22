package dev.requestportal.org.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl   where this service reaches Keycloak
 * @param realm     the application's realm
 * @param issuerUri the issuer printed in tokens (the public URL), which can differ from {@code baseUrl}
 */
@ConfigurationProperties("portal.keycloak")
public record KeycloakProperties(String baseUrl, String realm, String issuerUri) {
}
