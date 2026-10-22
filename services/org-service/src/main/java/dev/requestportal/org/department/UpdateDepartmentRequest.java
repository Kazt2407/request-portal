package dev.requestportal.org.department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The code is immutable because it appears in request numbers and access tokens.
 *
 * @param version the version the client last read, for optimistic locking
 */
public record UpdateDepartmentRequest(@NotBlank @Size(max = 200) String name, @NotNull Long version) {
}
