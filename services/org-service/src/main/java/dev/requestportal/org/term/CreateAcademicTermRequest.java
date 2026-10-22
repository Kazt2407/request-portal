package dev.requestportal.org.term;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateAcademicTermRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{4}-[0-9]{4}$", message = "must look like 2026-2027") String schoolYear,
        @NotNull @Min(1) @Max(3) Integer termNumber,
        @NotBlank @Size(max = 100) String name,
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn) {
}
