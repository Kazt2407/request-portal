package dev.requestportal.notification.notification;

import dev.requestportal.web.ConflictException;
import dev.requestportal.web.NotFoundException;
import dev.requestportal.web.PageResponse;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** For admins: what was sent to whom, and a way to resend what failed. */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasRole('ADMIN')")
class NotificationController {

    private final NotificationRepository notifications;
    private final Clock clock;

    NotificationController(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    PageResponse<NotificationView> list(
            @RequestParam(required = false) Long requestId,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationKind kind,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        List<Specification<Notification>> filters = new ArrayList<>();
        if (requestId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("requestId"), requestId));
        }
        if (status != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (kind != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("kind"), kind));
        }
        return PageResponse.of(notifications.findAll(Specification.allOf(filters), pageable), NotificationView::of);
    }

    @PostMapping("/{id}/retry")
    @Transactional
    NotificationView retry(@PathVariable Long id) {
        Notification notification = notifications.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification " + id + " not found"));
        if (notification.getStatus() == NotificationStatus.PENDING || notification.getStatus() == NotificationStatus.SENT) {
            throw new ConflictException("Notification " + id + " is " + notification.getStatus().name().toLowerCase()
                    + "; only failed or skipped notifications can be retried");
        }
        notification.retry(clock.instant());
        notifications.flush();
        return NotificationView.of(notification);
    }
}
