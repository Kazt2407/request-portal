package dev.requestportal.org.term;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Long> {

    List<AcademicTerm> findAllByOrderByStartsOnAsc();

    List<AcademicTerm> findBySchoolYearOrderByTermNumberAsc(String schoolYear);

    @Query("select distinct t.schoolYear from AcademicTerm t order by t.schoolYear desc")
    List<String> findSchoolYears();

    boolean existsBySchoolYearAndTermNumber(String schoolYear, short termNumber);

    /** The first existing term whose dates overlap the given range (both ends inclusive). */
    Optional<AcademicTerm> findFirstByStartsOnLessThanEqualAndEndsOnGreaterThanEqual(LocalDate endsOn, LocalDate startsOn);
}
