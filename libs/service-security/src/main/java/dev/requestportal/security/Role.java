package dev.requestportal.security;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/** Application roles, issued by Keycloak as realm roles in the {@code roles} token claim. */
public enum Role {
    STUDENT,
    LECTURER,
    STAFF,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    /** Keeps only the roles this application knows; Keycloak may add others such as its default roles. */
    public static Set<Role> fromClaims(Collection<String> names) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (names == null) {
            return roles;
        }
        for (Role role : values()) {
            if (names.contains(role.name())) {
                roles.add(role);
            }
        }
        return roles;
    }
}
