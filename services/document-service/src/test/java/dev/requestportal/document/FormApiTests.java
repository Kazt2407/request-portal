package dev.requestportal.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import dev.requestportal.document.form.TestDocuments;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class FormApiTests extends DocumentTestSupport {

    private static final byte[] CONFIRMATION = TestDocuments.docx("Vĩnh Long, ${today}", "Email: ${email}", 4,
            "ĐƠN XIN XÁC NHẬN",
            "Họ và tên: ${full_name}   MSSV: ${student_code}",
            "Lớp: ${class_name}   Ngày sinh: ${date_of_birth}",
            "Lý do: ${reason}",
            "Số tiền: ${amount} đồng (bằng chữ: ${amount_words} đồng)",
            "Hiệu lực từ: ${start_date}",
            "Ngày ${day} tháng ${month} năm ${year}");
    private static final String FIELDS = """
            [{"key": "reason", "label": "Reason", "type": "TEXT", "required": true},
             {"key": "amount", "label": "Loan amount", "type": "NUMBER"},
             {"key": "start_date", "label": "From", "type": "DATE"}]
            """;

    @Test
    void studentsDownloadTheFormFilledWithTheirDetails() throws Exception {
        long formId = createForm("Đơn xin xác nhận vay vốn", "STUDENT", true, uploadTemplate(CONFIRMATION), FIELDS);

        var result = fill(AN, formId, Map.of("reason", "Vay vốn sinh viên", "amount", "2.500.000",
                "start_date", "2026-11-01"));

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("filename*=UTF-8''%C4%90%C6%A1n%20xin");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(TestDocuments.text(result.getResponse().getContentAsByteArray())).containsExactly(
                "Vĩnh Long, " + today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                "ĐƠN XIN XÁC NHẬN",
                "Họ và tên: Nguyễn Văn Test   MSSV: 21004321",
                "Lớp: DHCNTT21A   Ngày sinh: 14/05/2003",
                "Lý do: Vay vốn sinh viên",
                "Số tiền: 2.500.000 đồng (bằng chữ: Hai triệu năm trăm nghìn đồng)",
                "Hiệu lực từ: 01/11/2026",
                "Ngày %02d tháng %02d năm %d".formatted(today.getDayOfMonth(), today.getMonthValue(), today.getYear()),
                "Email: user@example.edu");
    }

    @Test
    void unknownDetailsArePrintedAsDotsToFillInByHand() throws Exception {
        long formId = createForm("Xác nhận", "STUDENT", true, uploadTemplate(CONFIRMATION), FIELDS);

        var result = fill(BINH, formId, Map.of("reason", "Học bổng"));

        List<String> text = TestDocuments.text(result.getResponse().getContentAsByteArray());
        assertThat(text).contains("Họ và tên: Nguyễn Văn Test   MSSV: ……………………", "Hiệu lực từ: ……………………");
    }

    @Test
    void typedValuesAreChecked() throws Exception {
        long formId = createForm("Kiểm tra", "STUDENT", true, uploadTemplate(CONFIRMATION), FIELDS);

        var result = fill(AN, formId, Map.of("amount", "hai triệu", "start_date", "01/11/2026", "extra", "x"));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        var errors = json(result);
        assertThat(errors.read("$.errors.reason", String.class)).isEqualTo("is required");
        assertThat(errors.read("$.errors.amount", String.class)).startsWith("must be a whole number");
        assertThat(errors.read("$.errors.start_date", String.class)).isEqualTo("must be a date like 2026-10-22");
        assertThat(errors.read("$.errors.extra", String.class)).isEqualTo("is not a field of this form");
    }

    @Test
    void templatesMayOnlyUsePlaceholdersTheFormCanFill() throws Exception {
        UUID template = uploadTemplate(TestDocuments.docx(null, null, 50, "Mã: ${secret_code}, ${full_name}"));

        var result = createFormRequest("Hỏng", "STUDENT", true, template, "[]");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(json(result).read("$.errors.templateFileId", String.class))
                .isEqualTo("uses placeholders the form cannot fill: secret_code");
    }

    @Test
    void theTemplateMustBeAnUploadedFormTemplate() throws Exception {
        UUID pdf = upload(ADMIN, "not-a-template.pdf", "%PDF".getBytes(), "REQUEST_FIELD");

        var result = createFormRequest("Sai tệp", "STUDENT", true, pdf, "[]");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(json(result).read("$.errors.templateFileId", String.class))
                .isEqualTo("must be an uploaded .docx form template");
    }

    @Test
    void fieldKeysCannotShadowBuiltInValues() throws Exception {
        var result = createFormRequest("Trùng", "STUDENT", true, uploadTemplate(CONFIRMATION), """
                [{"key": "full_name", "label": "Name", "type": "TEXT"},
                 {"key": "fee_words", "label": "Fee in words", "type": "TEXT"}]
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(json(result).read("$.errors['fields[0].key']", String.class)).startsWith("is reserved");
        assertThat(json(result).read("$.errors['fields[1].key']", String.class)).startsWith("is reserved");
    }

    @Test
    void usersOnlySeeActiveFormsMeantForThem() throws Exception {
        UUID template = uploadTemplate(CONFIRMATION);
        long forStudents = createForm("Chỉ sinh viên", "STUDENT", true, template, FIELDS);
        long inactive = createForm("Đã ngừng", "ALL", false, uploadTemplate(CONFIRMATION), FIELDS);
        TestUser lecturer = TestUser.of("LECTURER", null);

        List<Integer> available = json(mvc.get().uri("/api/v1/forms/available").header(AUTHORIZATION, bearer(AN))
                .exchange()).read("$[*].id");
        assertThat(available).contains((int) forStudents).doesNotContain((int) inactive);
        assertThat(fill(lecturer, forStudents, Map.of("reason", "x"))).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/v1/forms/{id}", inactive).header(AUTHORIZATION, bearer(AN)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/v1/forms/{id}", inactive).header(AUTHORIZATION, bearer(ADMIN))).hasStatusOk();
        assertThat(status(template)).as("a template in use is kept").isEqualTo("ATTACHED");
    }

    private UUID uploadTemplate(byte[] docx) throws Exception {
        return upload(ADMIN, "template.docx", docx, "FORM_TEMPLATE", DOCX);
    }

    private long createForm(String title, String audience, boolean active, UUID template, String fields)
            throws Exception {
        var result = createFormRequest(title, audience, active, template, fields);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return json(result).read("$.id", Long.class);
    }

    private MvcTestResult createFormRequest(String title, String audience, boolean active, UUID template,
            String fields) {
        return mvc.post().uri("/api/v1/forms")
                .header(AUTHORIZATION, bearer(ADMIN))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"title": "%s", "audience": "%s", "active": %s, "templateFileId": "%s", "fields": %s}
                        """.formatted(title, audience, active, template, fields))
                .exchange();
    }

    private MvcTestResult fill(TestUser user, long formId, Map<String, String> values) {
        return mvc.post().uri("/api/v1/forms/{id}/document", formId)
                .header(AUTHORIZATION, bearer(user))
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("values", values)))
                .exchange();
    }
}
