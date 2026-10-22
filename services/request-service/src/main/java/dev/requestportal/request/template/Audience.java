package dev.requestportal.request.template;

import dev.requestportal.security.CurrentUser;
import dev.requestportal.security.Role;
import java.util.EnumSet;
import java.util.Set;

/** Who may submit requests from a template. */
public enum Audience {
    STUDENT,
    LECTURER,
    ALL;

    public boolean admits(CurrentUser user) {
        return switch (this) {
            case STUDENT -> user.hasRole(Role.STUDENT);
            case LECTURER -> user.hasRole(Role.LECTURER);
            case ALL -> user.hasRole(Role.STUDENT) || user.hasRole(Role.LECTURER);
        };
    }

    static Set<Audience> admitting(CurrentUser user) {
        Set<Audience> audiences = EnumSet.noneOf(Audience.class);
        for (Audience audience : values()) {
            if (audience.admits(user)) {
                audiences.add(audience);
            }
        }
        return audiences;
    }
}
