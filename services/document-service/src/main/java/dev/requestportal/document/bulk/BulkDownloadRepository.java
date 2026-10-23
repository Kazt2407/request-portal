package dev.requestportal.document.bulk;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BulkDownloadRepository extends JpaRepository<BulkDownload, UUID> {

    /** The oldest job waiting, or one whose worker died; skip locked so instances never share a job. */
    @Query(value = """
            select * from bulk_download
            where status = 'PENDING' or (status = 'RUNNING' and started_at < :staleBefore)
            order by created_at
            limit 1
            for update skip locked
            """, nativeQuery = true)
    List<BulkDownload> lockNext(Instant staleBefore);

    @Query(value = """
            select * from bulk_download where status = 'READY' and expires_at < :now
            limit 100 for update skip locked
            """, nativeQuery = true)
    List<BulkDownload> lockExpired(Instant now);
}
