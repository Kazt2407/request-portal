package dev.requestportal.document.file;

import dev.requestportal.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Upload: {@code POST /files} returns a pre-signed URL; the client PUTs the bytes there, then calls
 * {@code POST /files/{id}/complete}. Download: {@code GET /files/{id}/download} returns a pre-signed URL.
 */
@RestController
@RequestMapping("/api/v1/files")
class FileController {

    private final FileService fileService;

    FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping
    ResponseEntity<FileViews.UploadTicket> requestUpload(
            @Valid @RequestBody FileViews.NewUpload upload, CurrentUser user) {
        FileViews.UploadTicket ticket = fileService.requestUpload(upload, user);
        return ResponseEntity.created(URI.create("/api/v1/files/" + ticket.fileId())).body(ticket);
    }

    @PostMapping("/{id}/complete")
    FileViews.FileInfo completeUpload(@PathVariable UUID id, CurrentUser user) {
        return fileService.completeUpload(id, user);
    }

    @GetMapping("/{id}")
    FileViews.FileInfo get(@PathVariable UUID id, CurrentUser user) {
        return fileService.get(id, user);
    }

    @GetMapping("/{id}/download")
    FileViews.DownloadLink download(@PathVariable UUID id, CurrentUser user) {
        return fileService.downloadLink(id, user);
    }

    /** For request-service: can the caller submit these files? */
    @PostMapping("/checks")
    List<FileViews.FileCheck> check(@Valid @RequestBody FileViews.CheckRequest request, CurrentUser user) {
        return fileService.check(request.fileIds(), user);
    }
}
