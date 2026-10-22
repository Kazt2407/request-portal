package dev.requestportal.request.workflow;

import dev.requestportal.security.CurrentUser;
import java.util.UUID;

/** Who performed an action, as recorded on steps and in the history. */
public record Actor(UUID id, String name) {

    public static Actor of(CurrentUser user) {
        return new Actor(user.id(), user.fullName());
    }
}
