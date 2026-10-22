package dev.requestportal.request.workflow;

import dev.requestportal.security.CurrentUser;
import dev.requestportal.web.InvalidFieldsException;
import dev.requestportal.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for requesters (students and lecturers), plus the request detail everyone uses. */
@RestController
@RequestMapping("/api/v1/requests")
class RequestController {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final SubmissionService submissionService;
    private final RequestQueryService queryService;
    private final ProcessingService processingService;

    RequestController(
            SubmissionService submissionService,
            RequestQueryService queryService,
            ProcessingService processingService) {
        this.submissionService = submissionService;
        this.queryService = queryService;
        this.processingService = processingService;
    }

    /**
     * Submits a request. Send a unique {@code Idempotency-Key} (e.g. a UUID per form) to make retries safe:
     * a repeat returns the original request with {@code Idempotency-Replayed: true}.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('STUDENT', 'LECTURER')")
    ResponseEntity<RequestViews.Detail> submit(
            @Valid @RequestBody RequestBodies.Submit body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            CurrentUser user) {
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH)) {
            throw new InvalidFieldsException(Map.of("Idempotency-Key", "must be 1-100 characters"));
        }
        SubmissionService.Result result = submissionService.submit(body, idempotencyKey, user);
        return ResponseEntity.created(URI.create("/api/v1/requests/" + result.request().id()))
                .header("Idempotency-Replayed", String.valueOf(result.replayed()))
                .body(result.request());
    }

    @GetMapping("/mine")
    PageResponse<RequestViews.Summary> mine(
            @RequestParam(required = false) RequestStatus status,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable,
            CurrentUser user) {
        return queryService.mine(user, status, pageable);
    }

    @GetMapping("/{id}")
    RequestViews.Detail detail(@PathVariable Long id, CurrentUser user) {
        return queryService.detail(id, user);
    }

    @PostMapping("/{id}/cancel")
    RequestViews.Detail cancel(@PathVariable Long id, CurrentUser user) {
        return processingService.cancel(id, user);
    }
}
