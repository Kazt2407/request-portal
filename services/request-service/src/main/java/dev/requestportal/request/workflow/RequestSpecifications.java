package dev.requestportal.request.workflow;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

final class RequestSpecifications {

    private RequestSpecifications() {
    }

    static Specification<ServiceRequest> requestedBy(UUID requesterId) {
        return (root, query, cb) -> cb.equal(root.get("requesterId"), requesterId);
    }

    static Specification<ServiceRequest> hasStatus(RequestStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Requests with any step assigned to the department. */
    static Specification<ServiceRequest> involvingDepartment(String departmentCode) {
        return (root, query, cb) -> {
            Subquery<Long> steps = query.subquery(Long.class);
            Root<RequestStep> step = steps.from(RequestStep.class);
            steps.select(step.get("id")).where(
                    cb.equal(step.get("request"), root),
                    cb.equal(step.get("departmentCode"), departmentCode));
            return cb.exists(steps);
        };
    }

    /** Requests whose step in progress belongs to the department (there is at most one per request). */
    static Specification<ServiceRequest> awaitingDepartment(String departmentCode) {
        return (root, query, cb) -> {
            Subquery<Long> steps = query.subquery(Long.class);
            Root<RequestStep> step = steps.from(RequestStep.class);
            steps.select(step.get("id")).where(
                    cb.equal(step.get("request"), root),
                    cb.equal(step.get("status"), StepStatus.IN_PROGRESS),
                    cb.equal(step.get("departmentCode"), departmentCode));
            return cb.exists(steps);
        };
    }

    /** The filters every list shares; scoping by user is added by the caller. */
    static Specification<ServiceRequest> matching(RequestFilter filter, ZoneId zone) {
        List<Specification<ServiceRequest>> specs = new ArrayList<>();
        if (StringUtils.hasText(filter.q())) {
            String pattern = "%" + escapeLike(normalizeForSearch(filter.q())) + "%";
            specs.add((root, query, cb) -> cb.like(root.get("searchText"), pattern, '\\'));
        }
        if (filter.templateId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("templateId"), filter.templateId()));
        }
        if (filter.status() != null) {
            specs.add(hasStatus(filter.status()));
        }
        if (filter.resultReceived() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("resultReceived"), filter.resultReceived()));
        }
        if (filter.submittedFrom() != null) {
            var from = startOfDay(filter.submittedFrom(), zone);
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("submittedAt"), from));
        }
        if (filter.submittedTo() != null) {
            var before = startOfDay(filter.submittedTo().plusDays(1), zone);
            specs.add((root, query, cb) -> cb.lessThan(root.get("submittedAt"), before));
        }
        return Specification.allOf(specs);
    }

    /** Same folding as the database's search_text column: lower case, no Vietnamese diacritics. */
    static String normalizeForSearch(String text) {
        String withoutMarks = Normalizer.normalize(text.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutMarks.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    private static java.time.Instant startOfDay(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toInstant();
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
