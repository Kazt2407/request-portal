package dev.requestportal.request.workflow;

import dev.requestportal.request.file.FileDirectory;
import dev.requestportal.security.CurrentUser;
import dev.requestportal.web.BulkOperationException;
import dev.requestportal.web.BusinessRuleException;
import dev.requestportal.web.ConflictException;
import dev.requestportal.web.ForbiddenException;
import dev.requestportal.web.InvalidFieldsException;
import dev.requestportal.web.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff and requester actions on submitted requests. */
@Service
public class ProcessingService {

    private final ServiceRequestRepository requests;
    private final RequestQueryService queries;
    private final WorkflowEventSink events;
    private final FileDirectory fileDirectory;
    private final Clock clock;

    public ProcessingService(ServiceRequestRepository requests, RequestQueryService queries, WorkflowEventSink events,
            FileDirectory fileDirectory, Clock clock) {
        this.requests = requests;
        this.queries = queries;
        this.events = events;
        this.fileDirectory = fileDirectory;
        this.clock = clock;
    }

    @Transactional
    public RequestViews.Detail completeStep(
            Long id, int position, RequestBodies.CompleteStep body, CurrentUser user) {
        return change(id, user, (request, now) -> {
            RequestAccess.requireProcessor(request, step(request, position), user);
            checkAttachments(body.attachmentIdsOrEmpty());
            request.completeStep(position, Actor.of(user), body.note(), body.attachmentIdsOrEmpty(), now);
        });
    }

    @Transactional
    public RequestViews.Detail rejectStep(Long id, int position, RequestBodies.RejectStep body, CurrentUser user) {
        return change(id, user, (request, now) -> {
            RequestAccess.requireProcessor(request, step(request, position), user);
            request.rejectStep(position, Actor.of(user), body.reason().strip(), now);
        });
    }

    @Transactional
    public RequestViews.Detail cancel(Long id, CurrentUser user) {
        return change(id, user, (request, now) -> {
            RequestAccess.requireRequester(request, user);
            request.cancel(Actor.of(user), now);
        });
    }

    @Transactional
    public RequestViews.Detail setResultReceived(Long id, boolean received, CurrentUser user) {
        return change(id, user, (request, now) -> {
            RequestAccess.requireResultDepartment(request, user);
            request.markResultReceived(received, Actor.of(user), now);
        });
    }

    /** Completes the current step of every request, or of none if any of them cannot be completed. */
    @Transactional
    public RequestBodies.BulkResult completeCurrentSteps(List<Long> ids, String note, CurrentUser user) {
        return bulk(ids, user, (request, now) -> {
            RequestStep step = currentStepOf(request);
            RequestAccess.requireProcessor(request, step, user);
            request.completeStep(step.getPosition(), Actor.of(user), note, List.of(), now);
        });
    }

    @Transactional
    public RequestBodies.BulkResult rejectCurrentSteps(List<Long> ids, String reason, CurrentUser user) {
        return bulk(ids, user, (request, now) -> {
            RequestStep step = currentStepOf(request);
            RequestAccess.requireProcessor(request, step, user);
            request.rejectStep(step.getPosition(), Actor.of(user), reason.strip(), now);
        });
    }

    @Transactional
    public RequestBodies.BulkResult setResultsReceived(List<Long> ids, boolean received, CurrentUser user) {
        return bulk(ids, user, (request, now) -> {
            RequestAccess.requireResultDepartment(request, user);
            request.markResultReceived(received, Actor.of(user), now);
        });
    }

    private RequestViews.Detail change(Long id, CurrentUser user, BiConsumer<ServiceRequest, Instant> action) {
        ServiceRequest request = queries.findVisible(id, user);
        Instant now = clock.instant();
        action.accept(request, now);
        events.append(request);
        // Flush now so a concurrent change surfaces as a conflict here and the response shows the new version.
        requests.flush();
        return RequestViews.Detail.of(request, now);
    }

    /**
     * Applies the action to every request in id order, inside the caller's transaction. If any request
     * fails, the exception rolls the whole batch back and lists the reason per request id.
     */
    private RequestBodies.BulkResult bulk(
            List<Long> ids, CurrentUser user, BiConsumer<ServiceRequest, Instant> action) {
        List<Long> sortedIds = ids.stream().distinct().sorted().toList();
        Map<Long, ServiceRequest> found = requests.findAllById(sortedIds).stream()
                .collect(Collectors.toMap(ServiceRequest::getId, Function.identity()));
        Instant now = clock.instant();
        Map<String, String> failures = new LinkedHashMap<>();
        List<String> codes = new ArrayList<>();

        for (Long id : sortedIds) {
            ServiceRequest request = found.get(id);
            if (request == null || !RequestAccess.canView(request, user)) {
                failures.put(id.toString(), "Request " + id + " not found");
                continue;
            }
            try {
                action.accept(request, now);
                events.append(request);
                codes.add(request.getCode());
            } catch (ConflictException | BusinessRuleException | ForbiddenException | NotFoundException exception) {
                failures.put(id.toString(), exception.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new BulkOperationException(failures);
        }
        requests.flush();
        return new RequestBodies.BulkResult(codes.size(), codes);
    }

    /** Attachments must be files the staff member uploaded themselves and that no request uses yet. */
    private void checkAttachments(List<UUID> attachmentIds) {
        if (attachmentIds.isEmpty()) {
            return;
        }
        var checks = fileDirectory.check(attachmentIds);
        for (var id : attachmentIds) {
            String error = FieldValueValidator.fileError(checks.get(id), dev.requestportal.request.template.FieldType.FILE);
            if (error != null) {
                throw new InvalidFieldsException(Map.of("attachmentIds", error));
            }
        }
    }

    private static RequestStep step(ServiceRequest request, int position) {
        return request.step(position).orElseThrow(
                () -> new NotFoundException("Request " + request.getCode() + " has no step " + position));
    }

    private static RequestStep currentStepOf(ServiceRequest request) {
        return request.currentStep().orElseThrow(() -> new ConflictException(
                "Request " + request.getCode() + " is already " + request.getStatus().name().toLowerCase()));
    }
}
