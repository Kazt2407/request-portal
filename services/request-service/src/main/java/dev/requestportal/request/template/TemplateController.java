package dev.requestportal.request.template;

import dev.requestportal.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/templates")
class TemplateController {

    private final TemplateService templateService;

    TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    List<TemplateSummary> list(@RequestParam(required = false) TemplateStatus status) {
        return templateService.list(status);
    }

    /** What the signed-in student or lecturer can request. */
    @GetMapping("/available")
    List<TemplateSummary> available(CurrentUser user) {
        return templateService.available(user);
    }

    @GetMapping("/{id}")
    TemplateResponse get(@PathVariable Long id, CurrentUser user) {
        return templateService.get(id, user);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<TemplateResponse> create(@Valid @RequestBody TemplateRequest request) {
        TemplateResponse created = templateService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/templates/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    TemplateResponse update(@PathVariable Long id, @Valid @RequestBody TemplateRequest request) {
        return templateService.update(id, request);
    }
}
