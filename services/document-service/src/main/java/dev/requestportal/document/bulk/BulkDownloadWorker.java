package dev.requestportal.document.bulk;

import dev.requestportal.document.file.RequestAccess;
import dev.requestportal.document.file.RequestAccessRepository;
import dev.requestportal.document.file.StoredFile;
import dev.requestportal.document.storage.MultipartUploadStream;
import dev.requestportal.document.storage.ObjectStorage;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Builds queued archives one at a time per instance. A job is claimed in a short transaction, the ZIP is
 * streamed from storage back to storage without holding a database connection, and the result is recorded
 * in another short transaction. Jobs left RUNNING by a crashed instance are picked up again.
 */
@Component
public class BulkDownloadWorker {

    private static final Logger log = LoggerFactory.getLogger(BulkDownloadWorker.class);
    private static final DateTimeFormatter KEY_MONTH = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);

    private final BulkDownloadRepository downloads;
    private final BulkDownloadService service;
    private final RequestAccessRepository requestAccess;
    private final ObjectStorage storage;
    private final BulkDownloadProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    BulkDownloadWorker(BulkDownloadRepository downloads, BulkDownloadService service,
            RequestAccessRepository requestAccess, ObjectStorage storage, BulkDownloadProperties properties,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.downloads = downloads;
        this.service = service;
        this.requestAccess = requestAccess;
        this.storage = storage;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${portal.bulk-downloads.poll-interval}")
    public void buildNext() {
        Optional<BulkDownload> claimed = transaction.execute(status -> {
            Instant now = clock.instant();
            List<BulkDownload> next = downloads.lockNext(now.minus(properties.staleAfter()));
            next.forEach(download -> download.started(now));
            return next.stream().findFirst();
        });
        if (claimed == null || claimed.isEmpty()) {
            return;
        }
        BulkDownload download = claimed.get();
        String key = "bulk-exports/" + KEY_MONTH.format(clock.instant()) + "/" + download.getId() + ".zip";
        try {
            List<RequestAccess> requests = requestAccess.findAllById(download.getRequestIds());
            List<StoredFile> files = service.filesOf(requests);
            Map<Long, String> codes = new HashMap<>();
            requests.forEach(request -> codes.put(request.getRequestId(), request.getRequestCode()));
            long bytes = writeArchive(key, files, codes);
            record(download, stored -> stored.ready(key, files.size(), bytes, properties.ttl(), clock.instant()));
            log.info("Bulk download {} ready: {} files, {} bytes", download.getId(), files.size(), bytes);
        } catch (IOException | RuntimeException exception) {
            log.error("Bulk download {} failed", download.getId(), exception);
            record(download, stored -> stored.failed("The archive could not be built; try again", clock.instant()));
        }
    }

    /** Deletes archives past their time to live. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    public void deleteExpired() {
        transaction.executeWithoutResult(status -> downloads.lockExpired(clock.instant()).forEach(download -> {
            storage.delete(download.getObjectKey());
            download.expired();
        }));
    }

    /** One folder per request; repeated file names get a number, as a file manager would. */
    private long writeArchive(String key, List<StoredFile> files, Map<Long, String> codes) throws IOException {
        try (MultipartUploadStream upload = storage.create(key, "application/zip")) {
            ZipOutputStream zip = new ZipOutputStream(upload);
            Map<String, Integer> usedNames = new HashMap<>();
            for (StoredFile file : files) {
                zip.putNextEntry(new ZipEntry(uniqueName(codes.get(file.getRequestId()) + "/" + file.getFileName(), usedNames)));
                try (InputStream content = storage.open(file.getObjectKey())) {
                    content.transferTo(zip);
                }
                zip.closeEntry();
            }
            zip.finish();
            return upload.complete();
        }
    }

    static String uniqueName(String name, Map<String, Integer> usedNames) {
        int count = usedNames.merge(name, 1, Integer::sum);
        if (count == 1) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        return dot > name.lastIndexOf('/')
                ? name.substring(0, dot) + " (" + count + ")" + name.substring(dot)
                : name + " (" + count + ")";
    }

    private void record(BulkDownload download, Consumer<BulkDownload> change) {
        transaction.executeWithoutResult(status ->
                downloads.findById(download.getId()).ifPresent(change));
    }
}
