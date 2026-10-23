package dev.requestportal.document.event;

import dev.requestportal.document.file.FileService;
import dev.requestportal.document.file.RequestAccessUpdater;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps file ownership and access in step with the workflow: submitted files and step attachments move
 * to their request, and the request's requester and departments may then read them.
 */
@Component
class RequestEventHandler {

    private final JdbcClient jdbc;
    private final FileService files;
    private final RequestAccessUpdater access;
    private final Clock clock;

    RequestEventHandler(JdbcClient jdbc, FileService files, RequestAccessUpdater access, Clock clock) {
        this.jdbc = jdbc;
        this.files = files;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    void handle(RequestEvent event) {
        boolean firstTime = jdbc.sql("""
                        insert into processed_event (event_id, event_type, processed_at) values (:id, :type, :at)
                        on conflict (event_id) do nothing
                        """)
                .param("id", event.eventId())
                .param("type", event.type())
                .param("at", Timestamp.from(clock.instant()))
                .update() == 1;
        if (!firstTime) {
            return;
        }
        long requestId = event.request().id();
        switch (event.type()) {
            case "REQUEST_SUBMITTED" -> {
                access.requestSubmitted(requestId, event.request().code(), event.request().requester().id(),
                        event.strings("stepDepartments"));
                files.attach(event.uuids("fileIds"), event.request().requester().id(), requestId);
            }
            case "STEP_STARTED" -> access.departmentInvolved(requestId, event.text("departmentCode"));
            case "STEP_FINISHED" -> {
                if (event.finishedBy() != null) {
                    files.attach(event.uuids("attachmentIds"), event.finishedBy(), requestId);
                }
            }
            default -> {
                // Other events do not change files or access.
            }
        }
    }
}
