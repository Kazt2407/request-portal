package dev.requestportal.org.profile;

import dev.requestportal.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles")
class StudentProfileController {

    private final StudentProfileService profiles;

    StudentProfileController(StudentProfileService profiles) {
        this.profiles = profiles;
    }

    /** The signed-in student's own record, used to pre-fill forms. */
    @GetMapping("/me")
    ProfileViews.Profile me(CurrentUser user) {
        return profiles.get(user.id());
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    ProfileViews.Profile get(@PathVariable UUID accountId) {
        return profiles.get(accountId);
    }

    /** Staff correct the record; in production it is imported from the student information system. */
    @PutMapping("/{accountId}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    ProfileViews.Profile save(@PathVariable UUID accountId, @Valid @RequestBody ProfileViews.ProfileUpdate update) {
        return profiles.save(accountId, update);
    }
}
