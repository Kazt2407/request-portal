package dev.requestportal.notification.org;

import java.util.Optional;

/** Departments from org-service: display names and shared mailboxes. */
public interface DepartmentDirectory {

    record Department(String code, String name, String email) {
    }

    /**
     * @return the department, or empty if org-service does not know the code
     * @throws dev.requestportal.web.UpstreamServiceException if org-service cannot be reached
     */
    Optional<Department> find(String code);
}
