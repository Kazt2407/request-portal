package dev.requestportal.org.keycloak;

import dev.requestportal.security.ServiceAccountTokenInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class KeycloakAdminConfiguration {

    /** Client registration under {@code spring.security.oauth2.client.registration}. */
    static final String CLIENT_REGISTRATION_ID = "keycloak-admin";

    /** REST client for the Keycloak Admin API, authenticated as the {@code org-service} service account. */
    @Bean
    RestClient keycloakAdminRestClient(
            KeycloakProperties properties, ClientRegistrationRepository clientRegistrations) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl() + "/admin/realms/" + properties.realm())
                .requestInterceptor(new ServiceAccountTokenInterceptor(clientRegistrations, CLIENT_REGISTRATION_ID))
                .build();
    }
}
