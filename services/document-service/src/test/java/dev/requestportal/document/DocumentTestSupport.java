package dev.requestportal.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import dev.requestportal.document.form.ProfileDirectory;
import dev.requestportal.testsupport.TestJwtIssuer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/** One context for all tests: real PostgreSQL, Kafka and S3-compatible storage; tokens from TestJwtIssuer. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
abstract class DocumentTestSupport {

    private static final TestJwtIssuer issuer = TestJwtIssuer.start();
    private static final Configuration LENIENT_JSON =
            Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
    static final String PDF = "application/pdf";
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    /** The student record org-service returns for AN; other users have none. */
    static final ProfileDirectory.Profile AN_PROFILE = new ProfileDirectory.Profile("21004321",
            LocalDate.of(2003, 5, 14), "DHCNTT21A", "Faculty of Information Technology", "Information Technology",
            "0912 345 678");

    record TestUser(UUID id, String role, String department) {

        static TestUser of(String role, String department) {
            return new TestUser(UUID.randomUUID(), role, department);
        }
    }

    static final TestUser AN = TestUser.of("STUDENT", null);
    static final TestUser BINH = TestUser.of("STUDENT", null);
    static final TestUser ACAD_STAFF = TestUser.of("STAFF", "ACAD");
    static final TestUser FIN_STAFF = TestUser.of("STAFF", "FIN");
    static final TestUser STU_STAFF = TestUser.of("STAFF", "STU");
    static final TestUser ADMIN = TestUser.of("ADMIN", "IT");

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected KafkaTemplate<String, String> kafka;

    @Autowired
    protected JsonMapper json;

    @MockitoBean
    protected ProfileDirectory profiles;

    private final HttpClient http = HttpClient.newHttpClient();

    /** Answers like org-service would for the signed-in user of the current request. */
    @BeforeEach
    void stubProfiles() {
        given(profiles.mine()).willAnswer(invocation -> {
            var authentication = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
            return AN.id().toString().equals(authentication.getToken().getSubject())
                    ? Optional.of(AN_PROFILE)
                    : Optional.empty();
        });
    }

    @DynamicPropertySource
    static void useTestIssuer(DynamicPropertyRegistry registry) {
        registry.add("KEYCLOAK_URL", issuer::baseUrl);
        registry.add("KEYCLOAK_ISSUER_URI", () -> TestJwtIssuer.ISSUER);
    }

    static String bearer(TestUser user) {
        var token = issuer.token(user.role()).subject(user.id());
        if (user.department() != null) {
            token.department(user.department());
        }
        return token.bearer();
    }

    protected MvcTestResult requestUpload(TestUser user, String fileName, String contentType, long size, String purpose) {
        return mvc.post().uri("/api/v1/files")
                .header(AUTHORIZATION, bearer(user))
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "fileName", fileName, "contentType", contentType, "size", size, "purpose", purpose)))
                .exchange();
    }

    /** Requests an upload, PUTs the bytes to the pre-signed URL like a browser would, and completes it. */
    protected UUID upload(TestUser user, String fileName, byte[] content, String purpose) throws Exception {
        return upload(user, fileName, content, purpose, PDF);
    }

    protected UUID upload(TestUser user, String fileName, byte[] content, String purpose, String contentType)
            throws Exception {
        var ticket = requestUpload(user, fileName, contentType, content.length, purpose);
        assertThat(ticket).hasStatus(HttpStatus.CREATED);
        DocumentContext body = json(ticket);
        assertThat(put(body.read("$.uploadUrl"), content, contentType)).isEqualTo(200);
        UUID id = UUID.fromString(body.read("$.fileId"));
        assertThat(complete(user, id)).hasStatusOk();
        return id;
    }

    protected int put(String url, byte[] content, String contentType) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", contentType)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                        .build(), HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    protected HttpResponse<byte[]> get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    protected MvcTestResult complete(TestUser user, UUID fileId) {
        return mvc.post().uri("/api/v1/files/{id}/complete", fileId).header(AUTHORIZATION, bearer(user)).exchange();
    }

    protected MvcTestResult downloadLink(TestUser user, UUID fileId) {
        return mvc.get().uri("/api/v1/files/{id}/download", fileId).header(AUTHORIZATION, bearer(user)).exchange();
    }

    protected String status(UUID fileId) {
        return jdbc.sql("select status from stored_file where id = ?").param(fileId).query(String.class)
                .optional().orElse(null);
    }

    /** Publishes a workflow event as request-service would, and returns its id. */
    protected UUID publish(String type, long requestId, TestUser requester, Map<String, Object> data) throws Exception {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("type", type);
        envelope.put("version", 1);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("request", Map.of("id", requestId, "code", "ACAD-" + requestId, "title", "Transcript",
                "departmentCode", "ACAD", "requester", Map.of("id", requester.id().toString(), "name", "Nguyễn Văn An",
                        "email", "an@student.example.edu")));
        envelope.put("data", data);
        kafka.send("request-events", String.valueOf(requestId), json.writeValueAsString(envelope)).get();
        return eventId;
    }

    protected boolean processed(UUID eventId) {
        return jdbc.sql("select count(*) from processed_event where event_id = ?").param(eventId)
                .query(Integer.class).single() == 1;
    }

    protected static DocumentContext json(MvcTestResult result) throws Exception {
        return JsonPath.using(LENIENT_JSON).parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected static List<String> ids(UUID... ids) {
        return Arrays.stream(ids).map(UUID::toString).toList();
    }
}
