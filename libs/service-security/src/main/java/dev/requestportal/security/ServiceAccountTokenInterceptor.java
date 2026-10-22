package dev.requestportal.security;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Calls another API as this service (OAuth2 client credentials), for work that has no signed-in user,
 * such as background jobs. The token is cached and renewed by Spring Security's authorized-client manager.
 */
public final class ServiceAccountTokenInterceptor implements ClientHttpRequestInterceptor {

    private final OAuth2AuthorizedClientManager clientManager;
    private final OAuth2AuthorizeRequest authorizeRequest;

    /** @param registrationId a client-credentials registration under {@code spring.security.oauth2.client} */
    public ServiceAccountTokenInterceptor(ClientRegistrationRepository registrations, String registrationId) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                registrations, new InMemoryOAuth2AuthorizedClientService(registrations));
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        this.clientManager = manager;
        this.authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
                .principal(registrationId)
                .build();
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        OAuth2AuthorizedClient client = clientManager.authorize(authorizeRequest);
        if (client == null) {
            throw new IllegalStateException(
                    "No token for client registration " + authorizeRequest.getClientRegistrationId());
        }
        request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
        return execution.execute(request, body);
    }
}
