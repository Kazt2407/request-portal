package dev.requestportal.org.department;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDepartmentRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,15}$",
                message = "must be 2-16 upper-case letters, digits or underscores, starting with a letter")
        String code,
        @NotBlank @Size(max = 200) String name,
        @Email @Size(max = 320) String email) {
}
