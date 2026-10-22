package dev.requestportal.notification.notification;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository
        extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    /**
     * Locks a batch of notifications that are due. {@code skip locked} lets several instances send in
     * parallel without picking the same rows.
     */
    @Query(value = """
            select * from notification
            where status = 'PENDING' and next_attempt_at <= :now
            order by next_attempt_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<Notification> lockDue(Instant now, int limit);
}
