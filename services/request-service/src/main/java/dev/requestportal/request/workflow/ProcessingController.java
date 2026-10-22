package dev.requestportal.request.workflow;

import dev.requestportal.security.CurrentUser;
import dev.requestportal.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for staff processing requests, and the admin-wide request list. */
@RestController
@RequestMapping("/api/v1/requests")
class ProcessingController {

    private final RequestQueryService queryService;
    private final ProcessingService processingService;

    ProcessingController(RequestQueryService queryService, ProcessingService processingService) {
        this.queryService = queryService;
        this.processingService = processingService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    PageResponse<RequestViews.Summary> search(
            @ModelAttribute RequestFilter filter,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable,
            CurrentUser user) {
        return queryService.search(user, filter, pageable);
    }

    @PostMapping("/{id}/steps/{position}/complete")
    @PreAuthorize("hasRole('STAFF')")
    RequestViews.Detail completeStep(
            @PathVariable Long id,
            @PathVariable int position,
            @Valid @RequestBody RequestBodies.CompleteStep body,
            CurrentUser user) {
        return processingService.completeStep(id, position, body, user);
    }

    @PostMapping("/{id}/steps/{position}/reject")
    @PreAuthorize("hasRole('STAFF')")
    RequestViews.Detail rejectStep(
            @PathVariable Long id,
            @PathVariable int position,
            @Valid @RequestBody RequestBodies.RejectStep body,
            CurrentUser user) {
        return processingService.rejectStep(id, position, body, user);
    }

    @PostMapping("/bulk/complete")
    @PreAuthorize("hasRole('STAFF')")
    RequestBodies.BulkResult bulkComplete(@Valid @RequestBody RequestBodies.BulkComplete body, CurrentUser user) {
        return processingService.completeCurrentSteps(body.requestIds(), body.note(), user);
    }

    @PostMapping("/bulk/reject")
    @PreAuthorize("hasRole('STAFF')")
    RequestBodies.BulkResult bulkReject(@Valid @RequestBody RequestBodies.BulkReject body, CurrentUser user) {
        return processingService.rejectCurrentSteps(body.requestIds(), body.reason(), user);
    }

    @PutMapping("/{id}/result-received")
    @PreAuthorize("hasRole('STAFF')")
    RequestViews.Detail setResultReceived(
            @PathVariable Long id, @Valid @RequestBody RequestBodies.ResultReceived body, CurrentUser user) {
        return processingService.setResultReceived(id, body.received(), user);
    }

    @PutMapping("/result-received")
    @PreAuthorize("hasRole('STAFF')")
    RequestBodies.BulkResult setResultsReceived(
            @Valid @RequestBody RequestBodies.BulkResultReceived body, CurrentUser user) {
        return processingService.setResultsReceived(body.requestIds(), body.received(), user);
    }
}
