package dev.requestportal.document.bulk;

import dev.requestportal.document.file.FileStatus;
import dev.requestportal.document.file.RequestAccess;
import dev.requestportal.document.file.RequestAccessRepository;
import dev.requestportal.document.file.StoredFile;
import dev.requestportal.document.file.StoredFileRepository;
import dev.requestportal.document.storage.ObjectStorage;
import dev.requestportal.security.CurrentUser;
import dev.requestportal.security.Role;
import dev.requestportal.web.BusinessRuleException;
import dev.requestportal.web.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ZIP archives of many requests' attachments for staff. The selection is checked against the same access
 * rules as single downloads; requests the caller may not see are left out.
 */
@Service
public class BulkDownloadService {

    private final BulkDownloadRepository downloads;
    private final RequestAccessRepository requestAccess;
    private final StoredFileRepository files;
    private final ObjectStorage storage;
    private final BulkDownloadProperties properties;
    private final Clock clock;

    BulkDownloadService(BulkDownloadRepository downloads, RequestAccessRepository requestAccess,
            StoredFileRepository files, ObjectStorage storage, BulkDownloadProperties properties, Clock clock) {
        this.downloads = downloads;
        this.requestAccess = requestAccess;
        this.files = files;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BulkDownloadViews.Preview preview(List<Long> requestIds, CurrentUser user) {
        List<Long> distinct = requestIds.stream().distinct().toList();
        return preview(distinct.size(), visibleRequests(distinct, user));
    }

    @Transactional
    public BulkDownloadViews.Job start(List<Long> requestIds, CurrentUser user) {
        List<Long> distinct = requestIds.stream().distinct().toList();
        List<RequestAccess> visible = visibleRequests(distinct, user);
        BulkDownloadViews.Preview preview = preview(distinct.size(), visible);
        if (preview.problem() != null) {
            throw new BusinessRuleException(preview.problem());
        }
        List<Long> ids = visible.stream().map(RequestAccess::getRequestId).toList();
        return view(downloads.save(BulkDownload.pending(user.id(), ids, clock.instant())));
    }

    /** Only the person who asked for the archive (or an admin) can see it. */
    @Transactional(readOnly = true)
    public BulkDownloadViews.Job get(UUID id, CurrentUser user) {
        BulkDownload download = downloads.findById(id)
                .filter(found -> found.getRequestedBy().equals(user.id()) || user.hasRole(Role.ADMIN))
                .orElseThrow(() -> new NotFoundException("Bulk download " + id + " not found"));
        return view(download);
    }

    List<RequestAccess> visibleRequests(List<Long> requestIds, CurrentUser user) {
        if (requestIds.size() > properties.maxRequests()) {
            return List.of(); // too many to look up; the preview reports the limit
        }
        return requestAccess.findAllById(requestIds).stream()
                .filter(access -> user.hasRole(Role.ADMIN)
                        || (user.hasRole(Role.STAFF) && access.getDepartments().contains(user.departmentCode())))
                .toList();
    }

    List<StoredFile> filesOf(List<RequestAccess> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        return files.findByRequestIdInAndStatusOrderByRequestIdAscFileNameAsc(
                requests.stream().map(RequestAccess::getRequestId).toList(), FileStatus.ATTACHED);
    }

    private BulkDownloadViews.Preview preview(int requested, List<RequestAccess> visible) {
        List<StoredFile> found = filesOf(visible);
        long bytes = found.stream().mapToLong(StoredFile::getSizeBytes).sum();
        return new BulkDownloadViews.Preview(requested, visible.size(), found.size(), bytes,
                problem(requested, found.size(), bytes));
    }

    private String problem(int requests, int fileCount, long bytes) {
        if (requests > properties.maxRequests()) {
            return "Select at most " + properties.maxRequests() + " requests at a time";
        }
        if (fileCount == 0) {
            return "The selected requests have no files you can download";
        }
        if (fileCount > properties.maxFiles()) {
            return "The selection has " + fileCount + " files; select at most " + properties.maxFiles();
        }
        if (bytes > properties.maxSize().toBytes()) {
            return "The selection is " + (bytes >> 20) + " MB; select at most " + properties.maxSize().toMegabytes() + " MB";
        }
        return null;
    }

    private BulkDownloadViews.Job view(BulkDownload download) {
        String url = download.getStatus() == BulkDownloadStatus.READY
                ? storage.presignDownload(download.getObjectKey(), "request-files-" + download.getCreatedAt()
                        .toString().substring(0, 10) + ".zip").url()
                : null;
        return new BulkDownloadViews.Job(download.getId(), download.getStatus(), download.getRequestIds().size(),
                download.getFileCount(), download.getTotalBytes(), download.getError(), download.getCreatedAt(),
                download.getFinishedAt(), download.getExpiresAt(), url);
    }
}
