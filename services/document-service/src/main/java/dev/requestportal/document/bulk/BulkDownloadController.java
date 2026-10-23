package dev.requestportal.document.bulk;

import dev.requestportal.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Preview a selection, start building its archive, then poll until it has a download URL. */
@RestController
@RequestMapping("/api/v1/files/bulk-downloads")
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
class BulkDownloadController {

    private final BulkDownloadService downloads;

    BulkDownloadController(BulkDownloadService downloads) {
        this.downloads = downloads;
    }

    @PostMapping("/preview")
    BulkDownloadViews.Preview preview(@Valid @RequestBody BulkDownloadViews.Selection selection, CurrentUser user) {
        return downloads.preview(selection.requestIds(), user);
    }

    /** Starts the job; 202 Accepted with its URL, which reports READY and a download URL when done. */
    @PostMapping
    ResponseEntity<BulkDownloadViews.Job> start(@Valid @RequestBody BulkDownloadViews.Selection selection,
            CurrentUser user) {
        BulkDownloadViews.Job job = downloads.start(selection.requestIds(), user);
        return ResponseEntity.accepted().location(URI.create("/api/v1/files/bulk-downloads/" + job.id())).body(job);
    }

    @GetMapping("/{id}")
    BulkDownloadViews.Job get(@PathVariable UUID id, CurrentUser user) {
        return downloads.get(id, user);
    }
}
