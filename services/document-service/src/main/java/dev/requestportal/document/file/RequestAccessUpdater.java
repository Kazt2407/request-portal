package dev.requestportal.document.file;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Maintains {@link RequestAccess} from workflow events. */
@Component
public class RequestAccessUpdater {

    private final RequestAccessRepository requestAccess;
    private final Clock clock;

    RequestAccessUpdater(RequestAccessRepository requestAccess, Clock clock) {
        this.requestAccess = requestAccess;
        this.clock = clock;
    }

    @Transactional
    public void requestSubmitted(long requestId, String requestCode, UUID requesterId, List<String> stepDepartments) {
        if (!requestAccess.existsById(requestId)) {
            requestAccess.save(new RequestAccess(requestId, requestCode, requesterId, stepDepartments, clock.instant()));
        }
    }

    /** Covers requests submitted before events listed all step departments. */
    @Transactional
    public void departmentInvolved(long requestId, String departmentCode) {
        requestAccess.findById(requestId).ifPresent(access -> access.addDepartment(departmentCode, clock.instant()));
    }
}
