package dev.requestportal.document.form;

import java.time.LocalDate;
import java.util.Optional;

/** Student records from org-service. */
public interface ProfileDirectory {

    record Profile(String studentCode, LocalDate dateOfBirth, String className, String faculty, String major,
            String phone) {
    }

    /** The signed-in user's record, or empty if they have none (lecturers, students not yet imported). */
    Optional<Profile> mine();
}
