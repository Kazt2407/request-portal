package dev.requestportal.org.keycloak;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class KeycloakAdminConfiguration {

    /** Client registration under {@code spring.security.oauth2.client.registration}. */
    static final String CLIENT_REGISTRATION_ID = "keycloak-admin";

    /**
     * REST client for the Keycloak Admin API, authenticated as the {@code org-service} service account.
     * The client-credentials token is cached and renewed by the authorized-client manager.
     */
    @Bean
    RestClient keycloakAdminRestClient(
            KeycloakProperties properties, ClientRegistrationRepository clientRegistrations) {
        var clientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrations, new InMemoryOAuth2AuthorizedClientService(clientRegistrations));
        clientManager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(CLIENT_REGISTRATION_ID)
                .principal("org-service")
                .build();

        return RestClient.builder()
                .baseUrl(properties.baseUrl() + "/admin/realms/" + properties.realm())
                .requestInterceptor((request, body, execution) -> {
                    OAuth2AuthorizedClient client = clientManager.authorize(authorizeRequest);
                    request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
                    return execution.execute(request, body);
                })
                .build();
    }
}
