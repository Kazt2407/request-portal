package dev.requestportal.notification.reminder;

import dev.requestportal.notification.event.RequestEvent;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Local copy of the steps in progress, built from STEP_STARTED and STEP_FINISHED events, so reminders
 * need no call to request-service.
 */
@Component
public class TrackedSteps {

    private final JdbcClient jdbc;

    TrackedSteps(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void started(RequestEvent event) {
        jdbc.sql("""
                        insert into tracked_step (request_id, position, request_code, title, department_code,
                                                  description, started_at, due_at)
                        values (:request, :position, :code, :title, :department, :description, :startedAt, :dueAt)
                        on conflict (request_id, position) do nothing
                        """)
                .param("request", event.request().id())
                .param("position", event.number("position"))
                .param("code", event.request().code())
                .param("title", event.request().title())
                .param("department", event.text("departmentCode"))
                .param("description", event.text("description"))
                .param("startedAt", Timestamp.from(event.occurredAt()))
                .param("dueAt", Timestamp.from(event.instant("dueAt")))
                .update();
    }

    public void finished(RequestEvent event) {
        jdbc.sql("update tracked_step set finished_at = :at where request_id = :request and position = :position")
                .param("at", Timestamp.from(event.occurredAt()))
                .param("request", event.request().id())
                .param("position", event.number("position"))
                .update();
    }

    /**
     * Claims open steps due within {@code window} that have not been reminded. Steps whose whole deadline is
     * shorter than the window are left out: the "new request waiting" email already said when they are due.
     * The update marks and returns the rows in one statement, so two scheduler instances cannot both claim
     * the same step.
     */
    List<DueStep> claimDueSoon(Instant now, Duration window) {
        return jdbc.sql("""
                        update tracked_step set due_soon_notified_at = :now
                        where finished_at is null and due_soon_notified_at is null
                          and due_at > :now and due_at <= :horizon
                          and due_at - started_at > make_interval(secs => :windowSeconds)
                        returning request_id, position, request_code, title, department_code, description, due_at
                        """)
                .param("now", Timestamp.from(now))
                .param("horizon", Timestamp.from(now.plus(window)))
                .param("windowSeconds", window.toSeconds())
                .query(TrackedSteps::dueStep)
                .list();
    }

    /** Claims open steps past their deadline that have not been reported as overdue. */
    List<DueStep> claimOverdue(Instant now) {
        return jdbc.sql("""
                        update tracked_step set overdue_notified_at = :now
                        where finished_at is null and overdue_notified_at is null and due_at <= :now
                        returning request_id, position, request_code, title, department_code, description, due_at
                        """)
                .param("now", Timestamp.from(now))
                .query(TrackedSteps::dueStep)
                .list();
    }

    private static DueStep dueStep(java.sql.ResultSet row, int number) throws java.sql.SQLException {
        return new DueStep(row.getLong("request_id"), row.getInt("position"), row.getString("request_code"),
                row.getString("title"), row.getString("department_code"), row.getString("description"),
                row.getTimestamp("due_at").toInstant());
    }
}
