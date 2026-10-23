package dev.requestportal.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.jayway.jsonpath.DocumentContext;
import dev.requestportal.document.bulk.BulkDownloadWorker;
import dev.requestportal.document.storage.ObjectStorage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class BulkDownloadApiTests extends DocumentTestSupport {

    private static final AtomicLong requestIds = new AtomicLong(7000);

    @Autowired
    BulkDownloadWorker worker;

    @Autowired
    ObjectStorage storage;

    @Test
    void staffDownloadEveryAttachmentOfTheirRequestsInOneZip() throws Exception {
        long first = submitWithFiles(List.of("ACAD"), "transcript.pdf", "transcript.pdf");
        long second = submitWithFiles(List.of("ACAD", "FIN"), "id card.pdf");
        long otherDepartment = submitWithFiles(List.of("STU"), "secret.pdf");

        var preview = json(post("/preview", ACAD_STAFF, first, second, otherDepartment, first));
        assertThat(preview.read("$.requests", Integer.class)).isEqualTo(3);
        assertThat(preview.read("$.visibleRequests", Integer.class)).isEqualTo(2);
        assertThat(preview.read("$.files", Integer.class)).isEqualTo(3);
        assertThat(preview.read("$.problem", String.class)).isNull();

        var started = post("", ACAD_STAFF, first, second, otherDepartment);
        assertThat(started).hasStatus(HttpStatus.ACCEPTED);
        String location = started.getResponse().getHeader("Location");
        assertThat(json(started).read("$.status", String.class)).isEqualTo("PENDING");

        var ready = awaitReady(location, ACAD_STAFF);
        assertThat(ready.read("$.files", Integer.class)).isEqualTo(3);
        assertThat(ready.read("$.expiresAt", String.class)).isNotBlank();
        var zip = get(ready.read("$.downloadUrl"));
        assertThat(zip.statusCode()).isEqualTo(200);
        assertThat(entries(zip.body())).containsExactly(
                Map.entry("ACAD-" + first + "/transcript.pdf", "transcript.pdf of " + first),
                Map.entry("ACAD-" + first + "/transcript (2).pdf", "transcript.pdf of " + first),
                Map.entry("ACAD-" + second + "/id card.pdf", "id card.pdf of " + second));
    }

    @Test
    void aSelectionWithNothingToDownloadIsRejected() throws Exception {
        long otherDepartment = submitWithFiles(List.of("STU"), "secret.pdf");

        var result = post("", ACAD_STAFF, otherDepartment);

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(json(result).read("$.detail", String.class))
                .isEqualTo("The selected requests have no files you can download");
        long[] tooMany = LongStream.rangeClosed(1, 501).toArray();
        assertThat(json(post("/preview", ACAD_STAFF, tooMany)).read("$.problem", String.class))
                .isEqualTo("Select at most 500 requests at a time");
    }

    @Test
    void onlyTheStaffMemberWhoStartedAJobOrAnAdminCanSeeIt() throws Exception {
        long requestId = submitWithFiles(List.of("ACAD", "FIN"), "form.pdf");
        String location = post("", ACAD_STAFF, requestId).getResponse().getHeader("Location");

        assertThat(job(location, FIN_STAFF)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(job(location, ADMIN)).hasStatusOk();
        assertThat(job(location, AN)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(post("/preview", AN, requestId)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void aJobLeftRunningByACrashedInstanceIsBuiltAgain() throws Exception {
        long requestId = submitWithFiles(List.of("ACAD"), "retry.pdf");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into bulk_download (id, requested_by, request_ids, status, created_at, started_at)
                values (?, ?, ?::bigint[], 'RUNNING', ?, ?)
                """)
                .params(id, ACAD_STAFF.id(), "{" + requestId + "}", Timestamp.from(Instant.now()),
                        Timestamp.from(Instant.now().minus(Duration.ofHours(1))))
                .update();

        var ready = awaitReady("/api/v1/files/bulk-downloads/" + id, ACAD_STAFF);

        assertThat(entries(get(ready.read("$.downloadUrl")).body())).containsOnlyKeys("ACAD-" + requestId + "/retry.pdf");
    }

    @Test
    void archivesAreDeletedOnceTheyExpire() throws Exception {
        long requestId = submitWithFiles(List.of("ACAD"), "old.pdf");
        String location = post("", ACAD_STAFF, requestId).getResponse().getHeader("Location");
        awaitReady(location, ACAD_STAFF);
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        String key = jdbc.sql("select object_key from bulk_download where id = ?").param(id).query(String.class).single();
        jdbc.sql("update bulk_download set expires_at = ? where id = ?")
                .params(Timestamp.from(Instant.now().minusSeconds(1)), id)
                .update();

        worker.deleteExpired();

        assertThat(json(job(location, ACAD_STAFF)).read("$.status", String.class)).isEqualTo("EXPIRED");
        assertThat(json(job(location, ACAD_STAFF)).read("$.downloadUrl", String.class)).isNull();
        assertThat(storage.head(key)).isEmpty();
    }

    /** Uploads files as AN, submits a request with them and waits until document-service has attached them. */
    private long submitWithFiles(List<String> stepDepartments, String... fileNames) throws Exception {
        long requestId = requestIds.incrementAndGet();
        List<UUID> files = new ArrayList<>();
        for (String fileName : fileNames) {
            files.add(upload(AN, fileName, (fileName + " of " + requestId).getBytes(StandardCharsets.UTF_8),
                    "REQUEST_FIELD"));
        }
        UUID event = publish("REQUEST_SUBMITTED", requestId, AN, Map.of("stepCount", stepDepartments.size(),
                "fileIds", files.stream().map(UUID::toString).toList(), "stepDepartments", stepDepartments));
        await().atMost(Duration.ofSeconds(30)).until(() -> processed(event));
        return requestId;
    }

    private MvcTestResult post(String path, TestUser user, long... requestIds) {
        return mvc.post().uri("/api/v1/files/bulk-downloads" + path)
                .header(AUTHORIZATION, bearer(user))
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("requestIds", requestIds)))
                .exchange();
    }

    private MvcTestResult job(String location, TestUser user) {
        return mvc.get().uri(location).header(AUTHORIZATION, bearer(user)).exchange();
    }

    private DocumentContext awaitReady(String location, TestUser user) {
        return await().atMost(Duration.ofSeconds(30)).until(() -> json(job(location, user)),
                job -> "READY".equals(job.read("$.status", String.class)));
    }

    /** Entry names mapped to their text, in archive order. */
    private static Map<String, String> entries(byte[] archive) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
