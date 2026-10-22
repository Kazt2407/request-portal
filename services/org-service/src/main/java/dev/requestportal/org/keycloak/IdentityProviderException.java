package dev.requestportal.org.keycloak;

import dev.requestportal.web.UpstreamServiceException;

/** Keycloak could not be reached or rejected a call; the admin should retry later. */
public class IdentityProviderException extends UpstreamServiceException {

    public IdentityProviderException(String message, Throwable cause) {
        super(message, "The identity provider is unavailable; retry the change in a moment", cause);
    }
}
