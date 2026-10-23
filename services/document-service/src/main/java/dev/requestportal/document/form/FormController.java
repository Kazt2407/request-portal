package dev.requestportal.document.form;

import dev.requestportal.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/forms")
class FormController {

    private static final MediaType DOCX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final FormService forms;

    FormController(FormService forms) {
        this.forms = forms;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    List<FormViews.Form> list() {
        return forms.list();
    }

    /** Forms the signed-in student or lecturer can download. */
    @GetMapping("/available")
    List<FormViews.Form> available(CurrentUser user) {
        return forms.available(user);
    }

    @GetMapping("/{id}")
    FormViews.Form get(@PathVariable Long id, CurrentUser user) {
        return forms.get(id, user);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<FormViews.Form> create(@Valid @RequestBody FormViews.FormRequest request) {
        FormViews.Form created = forms.create(request);
        return ResponseEntity.created(URI.create("/api/v1/forms/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    FormViews.Form update(@PathVariable Long id, @Valid @RequestBody FormViews.FormRequest request) {
        return forms.update(id, request);
    }

    /** The filled-in .docx. Generating is CPU work, so the gateway rate-limits this endpoint more tightly. */
    @PostMapping("/{id}/document")
    ResponseEntity<byte[]> fill(@PathVariable Long id, @Valid @RequestBody FormViews.FillRequest request,
            CurrentUser user) {
        FormViews.FilledDocument document = forms.fill(id, request.values(), user);
        return ResponseEntity.ok()
                .contentType(DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(document.content());
    }
}
