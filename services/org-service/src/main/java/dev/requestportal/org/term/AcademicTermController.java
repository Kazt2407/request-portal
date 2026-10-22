package dev.requestportal.org.term;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/terms")
class AcademicTermController {

    private final AcademicTermService termService;

    AcademicTermController(AcademicTermService termService) {
        this.termService = termService;
    }

    @GetMapping
    List<AcademicTermResponse> list(@RequestParam(required = false) String schoolYear) {
        return termService.list(schoolYear);
    }

    /** Newest first. */
    @GetMapping("/school-years")
    List<String> schoolYears() {
        return termService.schoolYears();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<AcademicTermResponse> create(@Valid @RequestBody CreateAcademicTermRequest request) {
        AcademicTermResponse created = termService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/terms/" + created.id())).body(created);
    }
}
