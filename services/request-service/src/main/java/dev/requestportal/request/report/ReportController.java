package dev.requestportal.request.report;

import dev.requestportal.request.workflow.RequestFilter;
import dev.requestportal.security.CurrentUser;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/requests")
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
class ReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reports;

    ReportController(ReportService reports) {
        this.reports = reports;
    }

    /** The request list with the same filters as {@code GET /api/v1/requests}, as Excel. */
    @GetMapping("/export")
    ResponseEntity<byte[]> exportRequests(@ModelAttribute RequestFilter filter, CurrentUser user) {
        return xlsx("requests.xlsx", reports.exportRequests(user, filter));
    }

    @GetMapping("/statistics")
    RequestStatistics statistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedTo,
            @RequestParam(required = false) String departmentCode,
            @RequestParam(required = false) Long templateId,
            CurrentUser user) {
        return reports.statistics(user, submittedFrom, submittedTo, departmentCode, templateId);
    }

    @GetMapping("/statistics/export")
    ResponseEntity<byte[]> exportStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedTo,
            @RequestParam(required = false) String departmentCode,
            @RequestParam(required = false) Long templateId,
            CurrentUser user) {
        return xlsx("statistics.xlsx",
                reports.exportStatistics(user, submittedFrom, submittedTo, departmentCode, templateId));
    }

    private static ResponseEntity<byte[]> xlsx(String fileName, byte[] content) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(content);
    }
}
