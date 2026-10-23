package dev.requestportal.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReportApiTests extends ApiTestSupport {

    @Test
    void exportListsTheFilteredRequestsAsExcel() throws Exception {
        long templateId = createAcadThenFinTemplate();
        long waiting = submitValid(AN, templateId);
        long moved = submitValid(AN, templateId);
        complete(ACAD_STAFF, moved, 1);

        var result = mvc.get().uri("/api/v1/requests/export?templateId=" + templateId)
                .header(AUTHORIZATION, bearer(ACAD_STAFF))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentType()).contains("spreadsheetml");
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheet("Requests");
            assertThat(cells(sheet.getRow(0))).startsWith("Code", "Title", "Requester");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            List<List<String>> rows = List.of(cells(sheet.getRow(1)), cells(sheet.getRow(2)));
            assertThat(rows).extracting(row -> row.get(2)).containsOnly("Nguyễn Văn An");
            assertThat(rows).extracting(row -> row.get(5)).containsExactlyInAnyOrder("1. Check the records", "2. Settle fees");
            assertThat(sheet.getRow(1).getCell(9).getCellType()).as("submitted is a real date").isEqualTo(CellType.NUMERIC);
        }
        assertThat(json(mvc.get().uri("/api/v1/requests/{id}", waiting).header(AUTHORIZATION, bearer(AN)).exchange())
                .read("$.currentStep", Integer.class)).isEqualTo(1);
    }

    @Test
    void exportShowsOnlyRequestsTheCallerMaySee() throws Exception {
        long templateId = createAcadThenFinTemplate();
        submitValid(AN, templateId);

        var result = mvc.get().uri("/api/v1/requests/export?templateId=" + templateId)
                .header(AUTHORIZATION, bearer(STU_STAFF))
                .exchange();

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            assertThat(workbook.getSheet("Requests").getLastRowNum()).as("header only").isZero();
        }
        assertThat(mvc.get().uri("/api/v1/requests/export").header(AUTHORIZATION, bearer(AN)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void statisticsCountOutcomesByTemplateAndDepartment() throws Exception {
        long templateId = createAcadThenFinTemplate();
        long completed = submitValid(AN, templateId);
        complete(ACAD_STAFF, completed, 1);
        complete(FIN_STAFF, completed, 2);
        long rejected = submitValid(AN, templateId);
        assertThat(mvc.post().uri("/api/v1/requests/{id}/steps/1/reject", rejected)
                .header(AUTHORIZATION, bearer(ACAD_STAFF))
                .contentType(APPLICATION_JSON)
                .content("{\"reason\": \"Incomplete\"}")).hasStatusOk();
        long overdue = submitValid(AN, templateId);
        jdbc.sql("update request_step set due_at = ? where request_id = ? and position = 1")
                .params(Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS)), overdue)
                .update();
        long cancelled = submitValid(AN, templateId);
        assertThat(mvc.post().uri("/api/v1/requests/{id}/cancel", cancelled).header(AUTHORIZATION, bearer(AN)))
                .hasStatusOk();

        var admin = json(mvc.get().uri("/api/v1/requests/statistics?templateId=" + templateId)
                .header(AUTHORIZATION, bearer(ADMIN)).exchange());
        assertThat(admin.<Map<String, Object>>read("$.byTemplate[0]['total','inProgress','completed','rejected','cancelled','overdue']")
                .toString()).isEqualTo("{total=4, inProgress=1, completed=1, rejected=1, cancelled=1, overdue=1}");
        assertThat(admin.read("$.byTemplate[0].averageDaysToComplete", Double.class)).isGreaterThanOrEqualTo(0.0);
        assertThat(admin.<List<String>>read("$.byDepartment[*].departmentCode")).containsExactly("ACAD", "FIN");
        assertThat(admin.read("$.byDepartment[0]['inProgress','overdue','completed','rejected','onTimeRate']").toString())
                .isEqualTo("{inProgress=1, overdue=1, completed=1, rejected=1, onTimeRate=1.0}");
        assertThat(admin.read("$.byDepartment[1].completed", Integer.class)).isEqualTo(1);

        var finance = json(mvc.get().uri("/api/v1/requests/statistics?templateId=" + templateId + "&departmentCode=ACAD")
                .header(AUTHORIZATION, bearer(FIN_STAFF)).exchange());
        assertThat(finance.<List<String>>read("$.byDepartment[*].departmentCode"))
                .as("staff only see their own department, whatever they ask for").containsExactly("FIN");
    }

    @Test
    void statisticsExportHasASheetPerView() throws Exception {
        long templateId = createAcadThenFinTemplate();
        submitValid(AN, templateId);

        var result = mvc.get().uri("/api/v1/requests/statistics/export?templateId=" + templateId)
                .header(AUTHORIZATION, bearer(ADMIN))
                .exchange();

        assertThat(result).hasStatusOk();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            assertThat(List.of(workbook.getSheetName(0), workbook.getSheetName(1)))
                    .containsExactly("By template", "By department");
            assertThat(workbook.getSheet("By template").getRow(1).getCell(1).getNumericCellValue()).isEqualTo(1.0);
        }
    }

    @Test
    void aRequestDownloadsAsAWordDocumentForThoseWhoMaySeeIt() throws Exception {
        long requestId = submitValid(AN, createAcadThenFinTemplate());
        complete(ACAD_STAFF, requestId, 1);
        String code = detail(AN, requestId).read("$.code", String.class);

        var result = mvc.get().uri("/api/v1/requests/{id}/document", requestId).header(AUTHORIZATION, bearer(AN)).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains(code + ".docx");
        try (var document = new XWPFDocument(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
                var text = new XWPFWordExtractor(document)) {
            assertThat(text.getText()).contains("Code: " + code, "Status: In progress", "Nguyễn Văn An",
                    "Reason\tScholarship application", "Check the records", "Completed", "Lê Văn Cường", "Done",
                    "Step completed");
        }
        assertThat(mvc.get().uri("/api/v1/requests/{id}/document", requestId).header(AUTHORIZATION, bearer(FIN_STAFF)))
                .hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/requests/{id}/document", requestId).header(AUTHORIZATION, bearer(BINH)))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private static List<String> cells(Row row) {
        List<String> values = new ArrayList<>();
        row.forEach(cell -> values.add(cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : cell.toString()));
        return values;
    }
}
