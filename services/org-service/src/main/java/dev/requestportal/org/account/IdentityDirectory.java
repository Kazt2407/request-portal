package dev.requestportal.org.account;

import dev.requestportal.security.Role;
import java.util.Set;
import java.util.UUID;

/** The identity provider's user directory, which issues roles and department in access tokens. */
public interface IdentityDirectory {

    /** Sets the user's application roles and department code ({@code null} removes it). Idempotent. */
    void updateAccess(UUID userId, Set<Role> roles, String departmentCode);
}
