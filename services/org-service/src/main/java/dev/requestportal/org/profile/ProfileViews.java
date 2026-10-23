package dev.requestportal.org.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public final class ProfileViews {

    private ProfileViews() {
    }

    public record Profile(UUID accountId, String studentCode, LocalDate dateOfBirth, String className, String faculty,
            String major, String phone, Long version) {

        static Profile of(StudentProfile profile) {
            return new Profile(profile.getAccountId(), profile.getStudentCode(), profile.getDateOfBirth(),
                    profile.getClassName(), profile.getFaculty(), profile.getMajor(), profile.getPhone(),
                    profile.getVersion());
        }
    }

    /** @param version the version last read, or {@code null} when creating the profile */
    public record ProfileUpdate(
            @NotBlank @Pattern(regexp = "^[0-9A-Za-z]{6,20}$", message = "must be 6-20 letters or digits") String studentCode,
            @Past LocalDate dateOfBirth,
            @Size(max = 50) String className,
            @Size(max = 200) String faculty,
            @Size(max = 200) String major,
            @Size(max = 20) String phone,
            Long version) {
    }
}
