package dev.requestportal.notification.notification;

import dev.requestportal.notification.event.RequestEvent;
import dev.requestportal.notification.org.DepartmentDirectory;
import dev.requestportal.notification.reminder.DueStep;
import dev.requestportal.web.UpstreamServiceException;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders emails and queues them for {@link NotificationSender}. Rendering happens now, so the email shows
 * the request as it was when the event happened; the department mailbox is looked up only when sending.
 */
@Component
public class NotificationQueue {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final NotificationRepository notifications;
    private final TemplateEngine templates;
    private final DepartmentDirectory departments;
    private final MailProperties properties;
    private final Clock clock;

    NotificationQueue(NotificationRepository notifications, TemplateEngine templates, DepartmentDirectory departments,
            MailProperties properties, Clock clock) {
        this.notifications = notifications;
        this.templates = templates;
        this.departments = departments;
        this.properties = properties;
        this.clock = clock;
    }

    public void toRequester(RequestEvent event, NotificationKind kind, Map<String, Object> details) {
        RequestEvent.Request request = event.request();
        Map<String, Object> variables = variables(request.id(), request.code(), request.title(), details);
        variables.put("requesterName", request.requester().name());
        notifications.save(Notification.toEmail(event.eventId(), request.id(), request.code(), kind,
                request.requester().email(), subject(kind, request.code(), request.title()),
                render(kind, variables), clock.instant()));
    }

    public void toDepartment(RequestEvent event, NotificationKind kind, String departmentCode,
            Map<String, Object> details) {
        RequestEvent.Request request = event.request();
        Map<String, Object> variables = variables(request.id(), request.code(), request.title(), details);
        variables.put("requesterName", request.requester().name());
        variables.put("departmentName", departmentName(departmentCode));
        notifications.save(Notification.toDepartment(event.eventId(), request.id(), request.code(), kind,
                departmentCode, subject(kind, request.code(), request.title()),
                render(kind, variables), clock.instant()));
    }

    public void reminder(DueStep step, NotificationKind kind) {
        Map<String, Object> variables = variables(step.requestId(), step.requestCode(), step.title(),
                Map.of("stepDescription", step.description(), "dueAt", step.dueAt()));
        variables.put("departmentName", departmentName(step.departmentCode()));
        notifications.save(Notification.toDepartment(null, step.requestId(), step.requestCode(), kind,
                step.departmentCode(), subject(kind, step.requestCode(), step.title()),
                render(kind, variables), clock.instant()));
    }

    static String subject(NotificationKind kind, String code, String title) {
        String text = switch (kind) {
            case REQUEST_SUBMITTED -> "We received your request: " + title;
            case REQUEST_COMPLETED -> "Your request is ready: " + title;
            case REQUEST_REJECTED -> "Your request was rejected: " + title;
            case REQUEST_CANCELLED -> "Your request was cancelled: " + title;
            case STEP_WAITING -> "New request waiting for your department: " + title;
            case STEP_DUE_SOON -> "Due within a day: " + title;
            case STEP_OVERDUE -> "Overdue: " + title;
        };
        return "[" + code + "] " + text;
    }

    private Map<String, Object> variables(long requestId, String code, String title, Map<String, Object> details) {
        Map<String, Object> variables = new HashMap<>();
        details.forEach((key, value) -> {
            if (value instanceof Instant instant) {
                variables.put(key, DATE_TIME.format(instant.atZone(properties.timeZone())));
            } else {
                variables.put(key, value);
            }
            if (key.endsWith("DepartmentCode") && value instanceof String departmentCode) {
                variables.put(key.replace("DepartmentCode", "DepartmentName"), departmentName(departmentCode));
            }
        });
        variables.put("code", code);
        variables.put("title", title);
        variables.put("requestUrl", properties.portalUrl() + "/requests/" + requestId);
        return variables;
    }

    /** The department's name, or its code if org-service cannot tell right now; an email must not wait for it. */
    private String departmentName(String code) {
        try {
            return departments.find(code).map(DepartmentDirectory.Department::name).orElse(code);
        } catch (UpstreamServiceException exception) {
            return code;
        }
    }

    private String render(NotificationKind kind, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templates.process("mail/" + kind.templateName(), context);
    }

}
