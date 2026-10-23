package dev.requestportal.document.file;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    List<StoredFile> findByRequestIdInAndStatusOrderByRequestIdAscFileNameAsc(Collection<Long> requestIds,
            FileStatus status);

    /**
     * Unattached files whose last activity (issued, or checked for a submission) is older than the cutoff.
     * Locked with skip locked, so cleanup on several instances never picks the same rows.
     */
    @Query(value = """
            select * from stored_file
            where status <> 'ATTACHED' and greatest(created_at, coalesce(last_checked_at, created_at)) < :cutoff
            order by created_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<StoredFile> lockAbandoned(Instant cutoff, int limit);
}
