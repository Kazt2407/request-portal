package dev.requestportal.org.profile;

import dev.requestportal.web.ConflictException;
import dev.requestportal.web.NotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentProfileService {

    private final StudentProfileRepository profiles;

    StudentProfileService(StudentProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public ProfileViews.Profile get(UUID accountId) {
        return profiles.findById(accountId)
                .map(ProfileViews.Profile::of)
                .orElseThrow(() -> new NotFoundException("No student profile for account " + accountId));
    }

    /** Creates or replaces a profile; changing an existing one needs the version last read. */
    @Transactional
    public ProfileViews.Profile save(UUID accountId, ProfileViews.ProfileUpdate update) {
        StudentProfile profile = profiles.findById(accountId).orElseGet(() -> new StudentProfile(accountId));
        if (profile.getVersion() != null && !Objects.equals(profile.getVersion(), update.version())) {
            throw new ConflictException("The profile was changed by someone else; reload it and try again");
        }
        if (profiles.existsByStudentCodeAndAccountIdNot(update.studentCode().strip().toUpperCase(), accountId)) {
            throw new ConflictException("Student code " + update.studentCode() + " belongs to another student");
        }
        profile.update(update);
        return ProfileViews.Profile.of(profiles.saveAndFlush(profile));
    }
}
