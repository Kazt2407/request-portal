package dev.requestportal.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import dev.requestportal.document.file.FileCleanup;
import dev.requestportal.document.storage.ObjectStorage;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class FileApiTests extends DocumentTestSupport {

    private static final AtomicLong requestIds = new AtomicLong(5000);
    private static final byte[] CONTENT = "%PDF-1.7 transcript".getBytes(StandardCharsets.UTF_8);

    @Autowired
    FileCleanup cleanup;

    @Autowired
    ObjectStorage storage;

    @Test
    void uploadsAndDownloadsGoStraightToStorage() throws Exception {
        UUID id = upload(AN, "Bảng điểm học kỳ 1.pdf", CONTENT, "REQUEST_FIELD");

        var info = json(mvc.get().uri("/api/v1/files/{id}", id).header(AUTHORIZATION, bearer(AN)).exchange());
        assertThat(info.read("$.status", String.class)).isEqualTo("UPLOADED");
        assertThat(info.read("$.size", Integer.class)).isEqualTo(CONTENT.length);

        var link = downloadLink(AN, id);
        assertThat(link).hasStatusOk();
        var download = get(json(link).read("$.url"));
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(CONTENT);
        assertThat(download.headers().firstValue("Content-Disposition").orElseThrow())
                .contains("filename*=UTF-8''B%E1%BA%A3ng%20%C4%91i%E1%BB%83m");
    }

    @Test
    void completingBeforeUploadingIsAConflict() throws Exception {
        var ticket = json(requestUpload(AN, "late.pdf", PDF, 10, "REQUEST_FIELD"));

        assertThat(complete(AN, UUID.fromString(ticket.read("$.fileId")))).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void anUploadThatDoesNotMatchTheDeclaredSizeIsDiscarded() throws Exception {
        var ticket = json(requestUpload(AN, "lie.pdf", PDF, 1000, "REQUEST_FIELD"));
        UUID id = UUID.fromString(ticket.read("$.fileId"));
        put(ticket.read("$.uploadUrl"), CONTENT, PDF);

        assertThat(complete(AN, id)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(status(id)).isNull();
    }

    @Test
    void typeAndSizeAreCheckedBeforeIssuingAnUploadUrl() throws Exception {
        var result = requestUpload(AN, "virus.exe", "application/x-msdownload", 30_000_000, "REQUEST_FIELD");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(json(result).read("$.errors.contentType", String.class)).startsWith("must be one of");
        assertThat(json(result).read("$.errors.size", String.class)).isEqualTo("must be at most 20 MB");
        assertThat(requestUpload(AN, "form.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 100, "FORM_TEMPLATE"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void othersSeeAFileOnlyOnceItBelongsToARequestTheyMaySee() throws Exception {
        UUID id = upload(AN, "evidence.pdf", CONTENT, "REQUEST_FIELD");
        assertThat(downloadLink(ACAD_STAFF, id)).hasStatus(HttpStatus.NOT_FOUND);

        long requestId = requestIds.incrementAndGet();
        UUID event = publish("REQUEST_SUBMITTED", requestId, AN,
                Map.of("stepCount", 2, "fileIds", ids(id), "stepDepartments", List.of("ACAD", "FIN")));
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(event));

        assertThat(status(id)).isEqualTo("ATTACHED");
        assertThat(downloadLink(AN, id)).hasStatusOk();
        assertThat(downloadLink(ACAD_STAFF, id)).hasStatusOk();
        assertThat(downloadLink(FIN_STAFF, id)).hasStatusOk();
        assertThat(downloadLink(ADMIN, id)).hasStatusOk();
        assertThat(downloadLink(STU_STAFF, id)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(downloadLink(BINH, id)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void checksTellRequestServiceWhyAFileCannotBeUsed() throws Exception {
        UUID usable = upload(AN, "ok.pdf", CONTENT, "REQUEST_FIELD");
        UUID someoneElses = upload(BINH, "binh.pdf", CONTENT, "REQUEST_FIELD");
        UUID pending = UUID.fromString(json(requestUpload(AN, "half.pdf", PDF, 10, "REQUEST_FIELD")).read("$.fileId"));
        UUID attached = upload(AN, "used.pdf", CONTENT, "REQUEST_FIELD");
        UUID event = publish("REQUEST_SUBMITTED", requestIds.incrementAndGet(), AN,
                Map.of("stepCount", 1, "fileIds", ids(attached), "stepDepartments", List.of("ACAD")));
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(event));
        UUID unknown = UUID.randomUUID();

        var result = mvc.post().uri("/api/v1/files/checks")
                .header(AUTHORIZATION, bearer(AN))
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("fileIds", ids(usable, someoneElses, pending, attached, unknown))))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(json(result).<List<String>>read("$[*].problem")).containsExactly(
                null, "was uploaded by someone else", "has not finished uploading", "already belongs to a request",
                "does not exist");
        assertThat(json(result).read("$[0].contentType", String.class)).isEqualTo(PDF);
    }

    @Test
    void stepAttachmentsJoinTheRequestWhenTheStepFinishes() throws Exception {
        long requestId = requestIds.incrementAndGet();
        UUID submitted = publish("REQUEST_SUBMITTED", requestId, AN,
                Map.of("stepCount", 1, "fileIds", List.of(), "stepDepartments", List.of("ACAD")));
        UUID scan = upload(ACAD_STAFF, "stamped.pdf", CONTENT, "STEP_ATTACHMENT");

        UUID finished = publish("STEP_FINISHED", requestId, AN, Map.of("position", 1, "departmentCode", "ACAD",
                "status", "COMPLETED", "attachmentIds", ids(scan),
                "finishedBy", Map.of("id", ACAD_STAFF.id().toString(), "name", "Lê Văn Cường")));
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(submitted) && processed(finished));

        assertThat(status(scan)).isEqualTo("ATTACHED");
        assertThat(downloadLink(AN, scan)).as("the requester can download the result").hasStatusOk();
    }

    @Test
    void uploadsNeverAttachedAreDeletedFromStorage() throws Exception {
        UUID abandoned = upload(AN, "abandoned.pdf", CONTENT, "REQUEST_FIELD");
        UUID kept = upload(AN, "kept.pdf", CONTENT, "REQUEST_FIELD");
        UUID event = publish("REQUEST_SUBMITTED", requestIds.incrementAndGet(), AN,
                Map.of("stepCount", 1, "fileIds", ids(kept), "stepDepartments", List.of("ACAD")));
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(event));
        String abandonedKey = jdbc.sql("select object_key from stored_file where id = ?").param(abandoned)
                .query(String.class).single();
        jdbc.sql("update stored_file set created_at = ? where id in (?, ?)")
                .params(Timestamp.from(Instant.now().minus(Duration.ofDays(2))), abandoned, kept)
                .update();

        cleanup.deleteAbandonedUploads();

        assertThat(status(abandoned)).isNull();
        assertThat(storage.head(abandonedKey)).isEmpty();
        assertThat(status(kept)).isEqualTo("ATTACHED");
    }
}
