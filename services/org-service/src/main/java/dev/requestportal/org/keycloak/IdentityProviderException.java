package dev.requestportal.org.keycloak;

/** Keycloak could not be reached or rejected a call; the caller should retry later. */
public class IdentityProviderException extends RuntimeException {

    public IdentityProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
