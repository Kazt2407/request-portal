package dev.requestportal.request.workflow;

import dev.requestportal.security.CurrentUser;
import dev.requestportal.security.Role;
import dev.requestportal.web.NotFoundException;
import dev.requestportal.web.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestQueryService {

    private final ServiceRequestRepository requests;
    private final Clock clock;
    private final ZoneId zone;

    public RequestQueryService(ServiceRequestRepository requests, Clock clock, ZoneId portalZone) {
        this.requests = requests;
        this.clock = clock;
        this.zone = portalZone;
    }

    @Transactional(readOnly = true)
    public RequestViews.Detail detail(Long id, CurrentUser user) {
        return RequestViews.Detail.of(findVisible(id, user), clock.instant());
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestViews.Summary> mine(CurrentUser user, RequestStatus status, Pageable pageable) {
        Specification<ServiceRequest> spec = RequestSpecifications.requestedBy(user.id());
        if (status != null) {
            spec = spec.and(RequestSpecifications.hasStatus(status));
        }
        return page(spec, pageable);
    }

    /** Staff see requests involving their department; admins see all and may filter by department. */
    @Transactional(readOnly = true)
    public PageResponse<RequestViews.Summary> search(CurrentUser user, RequestFilter filter, Pageable pageable) {
        return scoped(user, filter)
                .map(spec -> page(spec, pageable))
                .orElseGet(() -> PageResponse.of(Page.<ServiceRequest>empty(pageable),
                        request -> RequestViews.Summary.of(request, clock.instant())));
    }

    /** The filter plus the caller's scope, or empty when the caller can see nothing (staff without department). */
    public Optional<Specification<ServiceRequest>> scoped(CurrentUser user, RequestFilter filter) {
        List<Specification<ServiceRequest>> specs = new ArrayList<>();
        specs.add(RequestSpecifications.matching(filter, zone));
        String ownDepartment = user.departmentCode();
        if (user.hasRole(Role.ADMIN)) {
            if (filter.departmentCode() != null) {
                specs.add(RequestSpecifications.involvingDepartment(filter.departmentCode()));
            }
        } else if (ownDepartment != null) {
            specs.add(RequestSpecifications.involvingDepartment(ownDepartment));
        } else {
            return Optional.empty();
        }
        if (Boolean.TRUE.equals(filter.awaitingMe()) && ownDepartment != null) {
            specs.add(RequestSpecifications.awaitingDepartment(ownDepartment));
        }
        return Optional.of(Specification.allOf(specs));
    }

    /** Requests the user may not see are reported as missing, so their existence is not leaked. */
    ServiceRequest findVisible(Long id, CurrentUser user) {
        return requests.findById(id)
                .filter(request -> RequestAccess.canView(request, user))
                .orElseThrow(() -> new NotFoundException("Request " + id + " not found"));
    }

    private PageResponse<RequestViews.Summary> page(Specification<ServiceRequest> spec, Pageable pageable) {
        Instant now = clock.instant();
        return PageResponse.of(requests.findAll(spec, pageable), request -> RequestViews.Summary.of(request, now));
    }
}
