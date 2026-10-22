package dev.requestportal.request.workflow;

import dev.requestportal.security.CurrentUser;
import dev.requestportal.security.Role;
import dev.requestportal.web.ForbiddenException;

/**
 * Who may see and change a request. Requesters see their own; staff see requests that involve their
 * department and act only on steps assigned to it; admins see everything but process nothing unless
 * they are also staff.
 */
final class RequestAccess {

    private RequestAccess() {
    }

    static boolean canView(ServiceRequest request, CurrentUser user) {
        return request.getRequesterId().equals(user.id())
                || user.hasRole(Role.ADMIN)
                || (user.hasRole(Role.STAFF) && request.involvesDepartment(user.departmentCode()));
    }

    static void requireProcessor(ServiceRequest request, RequestStep step, CurrentUser user) {
        if (!user.hasRole(Role.STAFF) || !step.getDepartmentCode().equals(user.departmentCode())) {
            throw new ForbiddenException("Step " + step.getPosition() + " of request " + request.getCode()
                    + " is handled by department " + step.getDepartmentCode());
        }
    }

    static void requireRequester(ServiceRequest request, CurrentUser user) {
        if (!request.getRequesterId().equals(user.id())) {
            throw new ForbiddenException("Only the requester can cancel request " + request.getCode());
        }
    }

    /** The department of the last step hands over the result, so it records whether it was collected. */
    static void requireResultDepartment(ServiceRequest request, CurrentUser user) {
        String department = request.lastStep().getDepartmentCode();
        if (!user.hasRole(Role.STAFF) || !department.equals(user.departmentCode())) {
            throw new ForbiddenException("The result of request " + request.getCode()
                    + " is handed over by department " + department);
        }
    }
}
