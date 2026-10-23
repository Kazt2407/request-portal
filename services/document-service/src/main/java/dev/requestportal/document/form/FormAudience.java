package dev.requestportal.document.form;

import dev.requestportal.security.CurrentUser;
import dev.requestportal.security.Role;

public enum FormAudience {
    STUDENT,
    LECTURER,
    ALL;

    boolean admits(CurrentUser user) {
        return switch (this) {
            case STUDENT -> user.hasRole(Role.STUDENT);
            case LECTURER -> user.hasRole(Role.LECTURER);
            case ALL -> user.hasRole(Role.STUDENT) || user.hasRole(Role.LECTURER);
        };
    }
}
