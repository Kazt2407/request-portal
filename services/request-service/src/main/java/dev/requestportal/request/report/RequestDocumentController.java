package dev.requestportal.request.report;

import dev.requestportal.request.workflow.RequestQueryService;
import dev.requestportal.request.workflow.RequestViews;
import dev.requestportal.security.CurrentUser;
import java.time.ZoneId;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** One request as a Word document, for whoever may see the request: its requester, involved staff, admins. */
@RestController
class RequestDocumentController {

    private static final MediaType DOCX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final RequestQueryService queries;
    private final ZoneId zone;

    RequestDocumentController(RequestQueryService queries, ZoneId portalZone) {
        this.queries = queries;
        this.zone = portalZone;
    }

    @GetMapping("/api/v1/requests/{id}/document")
    ResponseEntity<byte[]> document(@PathVariable Long id, CurrentUser user) {
        RequestViews.Detail request = queries.detail(id, user);
        return ResponseEntity.ok()
                .contentType(DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(request.code() + ".docx").build().toString())
                .body(RequestDocument.render(request, zone));
    }
}
