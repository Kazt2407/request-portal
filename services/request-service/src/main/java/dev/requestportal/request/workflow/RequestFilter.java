package dev.requestportal.request.workflow;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Query parameters of the staff request list. All are optional.
 *
 * @param q              code, requester name or email; accents are ignored
 * @param awaitingMe     only requests whose current step belongs to the caller's department
 * @param submittedFrom  first day, inclusive, in the portal's time zone
 * @param submittedTo    last day, inclusive
 * @param departmentCode admins only: requests with a step in this department
 */
public record RequestFilter(
        String q,
        Long templateId,
        RequestStatus status,
        Boolean awaitingMe,
        Boolean resultReceived,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedTo,
        String departmentCode) {
}
