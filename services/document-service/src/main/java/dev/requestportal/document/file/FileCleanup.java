package dev.requestportal.document.file;

import dev.requestportal.document.storage.ObjectStorage;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Deletes uploads that never became part of a request, from storage and from the table. */
@Component
public class FileCleanup {

    private static final Logger log = LoggerFactory.getLogger(FileCleanup.class);
    private static final int BATCH_SIZE = 100;

    private final StoredFileRepository files;
    private final ObjectStorage storage;
    private final FilesProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    FileCleanup(StoredFileRepository files, ObjectStorage storage, FilesProperties properties,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.files = files;
        this.storage = storage;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${portal.files.cleanup-interval}", initialDelayString = "${portal.files.cleanup-interval}")
    public void deleteAbandonedUploads() {
        Instant cutoff = clock.instant().minus(properties.unattachedTtl());
        int deleted;
        int total = 0;
        do {
            deleted = transaction.execute(status -> {
                List<StoredFile> abandoned = files.lockAbandoned(cutoff, BATCH_SIZE);
                for (StoredFile file : abandoned) {
                    storage.delete(file.getObjectKey());
                    files.delete(file);
                }
                return abandoned.size();
            });
            total += deleted;
        } while (deleted == BATCH_SIZE);
        if (total > 0) {
            log.info("Deleted {} uploads that were never attached (older than {})", total, cutoff);
        }
    }
}
