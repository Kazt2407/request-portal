package dev.requestportal.security;

import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/**
 * The signed-in user, read from the access token. Controllers can declare it as a method parameter.
 *
 * @param id             Keycloak user id (the {@code sub} claim)
 * @param departmentCode code of the user's department, or {@code null} if they have none
 */
public record CurrentUser(
        UUID id, String username, String email, String fullName, Set<Role> roles, String departmentCode) {

    public CurrentUser {
        roles = Set.copyOf(roles);
    }

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                fullName(jwt),
                Role.fromClaims(jwt.getClaimAsStringList("roles")),
                jwt.getClaimAsString("department"));
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    // Vietnamese names put the family name first ("Nguyễn Văn An"), but Keycloak's `name` claim puts it last.
    private static String fullName(Jwt jwt) {
        String family = jwt.getClaimAsString("family_name");
        String given = jwt.getClaimAsString("given_name");
        if (StringUtils.hasText(family) && StringUtils.hasText(given)) {
            return family + " " + given;
        }
        String name = jwt.getClaimAsString("name");
        return StringUtils.hasText(name) ? name : jwt.getClaimAsString("preferred_username");
    }
}
