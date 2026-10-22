package dev.requestportal.org.keycloak;

import static java.util.stream.Collectors.toSet;

import dev.requestportal.org.account.IdentityDirectory;
import dev.requestportal.web.NotFoundException;
import dev.requestportal.security.Role;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** {@link IdentityDirectory} backed by the Keycloak Admin REST API. */
@Component
class KeycloakIdentityDirectory implements IdentityDirectory {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {};
    private static final Set<String> PORTAL_ROLES = Arrays.stream(Role.values()).map(Role::name).collect(toSet());

    private final RestClient keycloak;

    KeycloakIdentityDirectory(RestClient keycloakAdminRestClient) {
        this.keycloak = keycloakAdminRestClient;
    }

    @Override
    public void updateAccess(UUID userId, Set<Role> roles, String departmentCode) {
        try {
            replaceRealmRoles(userId, roles.stream().map(Role::name).collect(toSet()));
            setDepartment(userId, departmentCode);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new NotFoundException("User " + userId + " no longer exists in the identity provider");
        } catch (RestClientException exception) {
            throw new IdentityProviderException("Could not update user " + userId + " in Keycloak", exception);
        }
    }

    /** Adds and removes portal roles only; Keycloak's own default roles are left alone. */
    private void replaceRealmRoles(UUID userId, Set<String> wanted) {
        List<Map<String, Object>> current = keycloak.get()
                .uri("/users/{id}/role-mappings/realm", userId)
                .retrieve()
                .body(JSON_ARRAY);
        Set<String> currentNames = current.stream().map(role -> (String) role.get("name")).collect(toSet());

        List<Map<String, Object>> toRemove = current.stream()
                .filter(role -> PORTAL_ROLES.contains(role.get("name")) && !wanted.contains(role.get("name")))
                .toList();
        List<Map<String, Object>> toAdd = wanted.stream()
                .filter(name -> !currentNames.contains(name))
                .map(name -> keycloak.get().uri("/roles/{name}", name).retrieve().body(JSON_OBJECT))
                .toList();

        if (!toAdd.isEmpty()) {
            keycloak.post()
                    .uri("/users/{id}/role-mappings/realm", userId)
                    .body(toAdd)
                    .retrieve()
                    .toBodilessEntity();
        }
        if (!toRemove.isEmpty()) {
            keycloak.method(HttpMethod.DELETE)
                    .uri("/users/{id}/role-mappings/realm", userId)
                    .body(toRemove)
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    /** Keycloak replaces attributes on update, so read the full user, change one attribute, write it back. */
    @SuppressWarnings("unchecked")
    private void setDepartment(UUID userId, String departmentCode) {
        Map<String, Object> user = new HashMap<>(keycloak.get()
                .uri("/users/{id}", userId)
                .retrieve()
                .body(JSON_OBJECT));
        Map<String, Object> attributes = new HashMap<>();
        if (user.get("attributes") instanceof Map<?, ?> existing) {
            attributes.putAll((Map<String, Object>) existing);
        }
        if (departmentCode == null) {
            attributes.remove("department");
        } else {
            attributes.put("department", List.of(departmentCode));
        }
        user.put("attributes", attributes);

        keycloak.put().uri("/users/{id}", userId).body(user).retrieve().toBodilessEntity();
    }
}
