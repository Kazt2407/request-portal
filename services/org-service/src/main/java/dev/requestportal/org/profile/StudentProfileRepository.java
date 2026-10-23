package dev.requestportal.org.profile;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

    boolean existsByStudentCodeAndAccountIdNot(String studentCode, UUID accountId);
}
