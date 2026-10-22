package dev.requestportal.request.org;

import java.util.Collection;
import java.util.Set;

/** Reference data owned by org-service. */
public interface OrgDirectory {

    /** The codes from {@code codes} that are not existing departments. */
    Set<String> unknownDepartments(Collection<String> codes);

    boolean termExists(String termCode);

    boolean schoolYearExists(String schoolYear);
}
