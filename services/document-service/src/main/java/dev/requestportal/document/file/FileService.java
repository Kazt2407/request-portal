package dev.requestportal.document.file;

import dev.requestportal.document.storage.ObjectStorage;
import dev.requestportal.security.CurrentUser;
import dev.requestportal.security.Role;
import dev.requestportal.web.BusinessRuleException;
import dev.requestportal.web.ConflictException;
import dev.requestportal.web.ForbiddenException;
import dev.requestportal.web.InvalidFieldsException;
import dev.requestportal.web.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Files never pass through this service: clients upload to and download from object storage with
 * short-lived pre-signed URLs, and this service decides who gets one.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final StoredFileRepository files;
    private final RequestAccessRepository requestAccess;
    private final ObjectStorage storage;
    private final FilesProperties properties;
    private final Clock clock;

    FileService(StoredFileRepository files, RequestAccessRepository requestAccess, ObjectStorage storage,
            FilesProperties properties, Clock clock) {
        this.files = files;
        this.requestAccess = requestAccess;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public FileViews.UploadTicket requestUpload(FileViews.NewUpload upload, CurrentUser user) {
        if (upload.purpose() == FilePurpose.FORM_TEMPLATE && !user.hasRole(Role.ADMIN)) {
            throw new ForbiddenException("Only admins upload form templates");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        String contentType = upload.contentType().strip().toLowerCase();
        if (!upload.purpose().allows(contentType)) {
            errors.put("contentType", "must be one of " + new TreeSet<>(upload.purpose().allowedContentTypes()));
        }
        if (upload.size() > properties.maxSize().toBytes()) {
            errors.put("size", "must be at most " + properties.maxSize().toMegabytes() + " MB");
        }
        if (!errors.isEmpty()) {
            throw new InvalidFieldsException(errors);
        }

        StoredFile file = files.save(StoredFile.pending(
                user.id(), upload.purpose(), safeFileName(upload.fileName()), contentType, upload.size(), clock.instant()));
        ObjectStorage.PresignedUrl url = storage.presignUpload(file.getObjectKey(), contentType);
        return new FileViews.UploadTicket(file.getId(), url.url(), "PUT", Map.of("Content-Type", contentType),
                url.expiresAt());
    }

    /** The client says the upload finished; check it really is in storage and matches what was declared. */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public FileViews.FileInfo completeUpload(UUID id, CurrentUser user) {
        StoredFile file = files.findById(id)
                .filter(found -> found.getOwnerId().equals(user.id()))
                .orElseThrow(() -> new NotFoundException("File " + id + " not found"));
        if (file.getStatus() != FileStatus.PENDING) {
            return FileViews.FileInfo.of(file);
        }
        ObjectStorage.ObjectInfo object = storage.head(file.getObjectKey())
                .orElseThrow(() -> new ConflictException("File " + id + " has not been uploaded yet"));
        if (object.size() != file.getSizeBytes() || object.size() > properties.maxSize().toBytes()) {
            storage.delete(file.getObjectKey());
            files.delete(file);
            throw new BusinessRuleException("The uploaded file is " + object.size() + " bytes, but " + file.getSizeBytes()
                    + " were declared; request a new upload");
        }
        file.uploaded(clock.instant());
        return FileViews.FileInfo.of(file);
    }

    @Transactional(readOnly = true)
    public FileViews.FileInfo get(UUID id, CurrentUser user) {
        return FileViews.FileInfo.of(findReadable(id, user));
    }

    @Transactional(readOnly = true)
    public FileViews.DownloadLink downloadLink(UUID id, CurrentUser user) {
        StoredFile file = findReadable(id, user);
        if (file.getStatus() == FileStatus.PENDING) {
            throw new ConflictException("File " + id + " has not been uploaded yet");
        }
        ObjectStorage.PresignedUrl url = storage.presignDownload(file.getObjectKey(), file.getFileName());
        return new FileViews.DownloadLink(url.url(), url.expiresAt());
    }

    /**
     * Tells request-service whether the caller may put these files into a submission: they must exist, be
     * theirs, be uploaded and not belong to another request yet. Usable files are protected from cleanup.
     */
    @Transactional
    public List<FileViews.FileCheck> check(List<UUID> ids, CurrentUser user) {
        Instant now = clock.instant();
        Map<UUID, StoredFile> found = new LinkedHashMap<>();
        files.findAllById(ids).forEach(file -> found.put(file.getId(), file));
        return ids.stream().distinct().map(id -> {
            StoredFile file = found.get(id);
            String problem = problemWith(file, user);
            if (problem == null) {
                file.checked(now);
            }
            return file == null
                    ? new FileViews.FileCheck(id, null, null, problem)
                    : new FileViews.FileCheck(id, file.getFileName(), file.getContentType(), problem);
        }).toList();
    }

    /** An uploaded .docx form template, ready to be used by a form. */
    @Transactional(readOnly = true)
    public StoredFile formTemplate(UUID id) {
        return files.findById(id)
                .filter(file -> file.getPurpose() == FilePurpose.FORM_TEMPLATE && file.getStatus() != FileStatus.PENDING)
                .orElseThrow(() -> new InvalidFieldsException(
                        Map.of("templateFileId", "must be an uploaded .docx form template")));
    }

    /** The file is in use (by a form) and must not be cleaned up. */
    @Transactional
    public void keep(UUID id) {
        files.findById(id).filter(file -> file.getStatus() == FileStatus.UPLOADED).ifPresent(file -> file.keep(clock.instant()));
    }

    public byte[] read(StoredFile file) {
        return storage.read(file.getObjectKey());
    }

    /** From a workflow event: the files now belong to the request. Only the uploader's own files move. */
    @Transactional
    public void attach(Collection<UUID> ids, UUID owner, long requestId) {
        Instant now = clock.instant();
        for (StoredFile file : files.findAllById(ids)) {
            if (!file.getOwnerId().equals(owner) || !file.attachTo(requestId, now)) {
                log.warn("File {} could not be attached to request {} (owner {}, status {})",
                        file.getId(), requestId, file.getOwnerId(), file.getStatus());
            }
        }
    }

    private StoredFile findReadable(UUID id, CurrentUser user) {
        return files.findById(id)
                .filter(file -> canRead(file, user))
                .orElseThrow(() -> new NotFoundException("File " + id + " not found"));
    }

    /** The uploader, admins, and whoever may see the request the file belongs to. */
    private boolean canRead(StoredFile file, CurrentUser user) {
        if (file.getOwnerId().equals(user.id()) || user.hasRole(Role.ADMIN)) {
            return true;
        }
        if (file.getRequestId() == null) {
            return false;
        }
        Optional<RequestAccess> access = requestAccess.findById(file.getRequestId());
        return access.isPresent() && (access.get().getRequesterId().equals(user.id())
                || (user.hasRole(Role.STAFF) && access.get().getDepartments().contains(user.departmentCode())));
    }

    private static String problemWith(StoredFile file, CurrentUser user) {
        if (file == null) {
            return "does not exist";
        }
        if (!file.getOwnerId().equals(user.id())) {
            return "was uploaded by someone else";
        }
        return switch (file.getStatus()) {
            case PENDING -> "has not finished uploading";
            case ATTACHED -> "already belongs to a request";
            case UPLOADED -> null;
        };
    }

    /** Keeps only the last path segment and drops control characters. */
    private static String safeFileName(String name) {
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        return base.isEmpty() ? "file" : base;
    }
}
