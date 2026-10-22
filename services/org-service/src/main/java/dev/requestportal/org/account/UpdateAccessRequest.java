package dev.requestportal.org.account;

import dev.requestportal.security.Role;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

/**
 * The complete desired access of an account. Replaces the current roles and department, so repeating the
 * same request is safe.
 *
 * @param departmentId the department, or {@code null} for none (not allowed for staff)
 */
public record UpdateAccessRequest(@NotEmpty Set<Role> roles, Long departmentId) {
}
