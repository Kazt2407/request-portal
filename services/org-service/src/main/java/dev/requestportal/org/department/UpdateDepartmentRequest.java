package dev.requestportal.org.department;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Replaces the editable fields; the code is immutable because it appears in request numbers and tokens.
 *
 * @param email   shared mailbox, or {@code null} for none
 * @param version the version the client last read, for optimistic locking
 */
public record UpdateDepartmentRequest(
        @NotBlank @Size(max = 200) String name, @Email @Size(max = 320) String email, @NotNull Long version) {
}
