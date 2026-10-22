package dev.requestportal.org.term;

import dev.requestportal.web.ConflictException;
import dev.requestportal.web.InvalidFieldsException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicTermService {

    private final AcademicTermRepository terms;

    public AcademicTermService(AcademicTermRepository terms) {
        this.terms = terms;
    }

    @Transactional(readOnly = true)
    public List<AcademicTermResponse> list(String schoolYear) {
        List<AcademicTerm> found = schoolYear == null
                ? terms.findAllByOrderByStartsOnAsc()
                : terms.findBySchoolYearOrderByTermNumberAsc(schoolYear);
        return found.stream().map(AcademicTermResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<String> schoolYears() {
        return terms.findSchoolYears();
    }

    @Transactional
    public AcademicTermResponse create(CreateAcademicTermRequest request) {
        validate(request);
        short termNumber = request.termNumber().shortValue();
        if (terms.existsBySchoolYearAndTermNumber(request.schoolYear(), termNumber)) {
            throw new ConflictException(
                    "Term " + request.termNumber() + " of " + request.schoolYear() + " already exists");
        }
        terms.findFirstByStartsOnLessThanEqualAndEndsOnGreaterThanEqual(request.endsOn(), request.startsOn())
                .ifPresent(overlapping -> {
                    throw new ConflictException("The dates overlap " + overlapping.getName());
                });
        AcademicTerm term = terms.save(new AcademicTerm(
                request.schoolYear(), termNumber, request.name().strip(), request.startsOn(), request.endsOn()));
        return AcademicTermResponse.from(term);
    }

    private static void validate(CreateAcademicTermRequest request) {
        Map<String, String> errors = new HashMap<>();
        int firstYear = Integer.parseInt(request.schoolYear().substring(0, 4));
        int secondYear = Integer.parseInt(request.schoolYear().substring(5));
        if (secondYear != firstYear + 1) {
            errors.put("schoolYear", "must span two consecutive years, e.g. 2026-2027");
        }
        if (!request.endsOn().isAfter(request.startsOn())) {
            errors.put("endsOn", "must be after startsOn");
        }
        if (!errors.isEmpty()) {
            throw new InvalidFieldsException(errors);
        }
    }
}
