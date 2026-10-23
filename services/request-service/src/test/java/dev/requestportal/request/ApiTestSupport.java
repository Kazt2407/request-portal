package dev.requestportal.request;

import static java.util.stream.Collectors.toCollection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import dev.requestportal.request.file.FileDirectory;
import dev.requestportal.request.org.OrgDirectory;
import dev.requestportal.testsupport.TestJwtIssuer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * One Spring context and one PostgreSQL for all API tests. Tokens are signed by {@link TestJwtIssuer},
 * and org-service is replaced by a mock that knows a fixed set of departments and terms.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiTestSupport {

    static final Set<String> DEPARTMENTS = Set.of("ACAD", "FIN", "STU", "IT");
    static final Set<String> TERMS = Set.of("2026-2027-1", "2026-2027-2");
    static final Set<String> SCHOOL_YEARS = Set.of("2025-2026", "2026-2027");
    /** document-service says this file was uploaded by someone else; every other id is a usable PDF. */
    static final UUID FOREIGN_FILE = UUID.fromString("f0000000-0000-4000-8000-000000000001");
    /** document-service says this file is a usable PNG image. */
    static final UUID IMAGE_FILE = UUID.fromString("f0000000-0000-4000-8000-000000000002");

    static final TestUser AN = new TestUser("Nguyễn", "Văn An", "an.nguyen@student.example.edu", null, "STUDENT");
    static final TestUser BINH = new TestUser("Trần", "Thị Bình", "binh.tran@student.example.edu", null, "STUDENT");
    static final TestUser LECTURER = new TestUser("Võ", "Thanh Hải", "hai.vo@example.edu", null, "LECTURER");
    static final TestUser ACAD_STAFF = new TestUser("Lê", "Văn Cường", "cuong.le@example.edu", "ACAD", "STAFF");
    static final TestUser FIN_STAFF = new TestUser("Phạm", "Thị Dung", "dung.pham@example.edu", "FIN", "STAFF");
    static final TestUser STU_STAFF = new TestUser("Đặng", "Quốc Khánh", "khanh.dang@example.edu", "STU", "STAFF");
    static final TestUser ADMIN = new TestUser("Hoàng", "Minh Em", "admin@example.edu", "IT", "ADMIN");

    private static final TestJwtIssuer issuer = TestJwtIssuer.start();
    private static final Configuration LENIENT_JSON =
            Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
    private static final AtomicInteger templateCounter = new AtomicInteger();

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected JdbcClient jdbc;

    @MockitoBean
    protected OrgDirectory orgDirectory;

    @MockitoBean
    protected FileDirectory fileDirectory;

    @DynamicPropertySource
    static void useTestIssuer(DynamicPropertyRegistry registry) {
        registry.add("KEYCLOAK_URL", issuer::baseUrl);
        registry.add("KEYCLOAK_ISSUER_URI", () -> TestJwtIssuer.ISSUER);
    }

    @BeforeEach
    void stubOrgDirectory() {
        given(orgDirectory.unknownDepartments(any())).willAnswer(invocation -> {
            Collection<String> codes = invocation.getArgument(0);
            return codes.stream().filter(code -> !DEPARTMENTS.contains(code)).collect(toCollection(TreeSet::new));
        });
        given(orgDirectory.termExists(anyString())).willAnswer(invocation -> TERMS.contains(invocation.<String>getArgument(0)));
        given(orgDirectory.schoolYearExists(anyString()))
                .willAnswer(invocation -> SCHOOL_YEARS.contains(invocation.<String>getArgument(0)));
        given(fileDirectory.check(any())).willAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(0);
            return ids.stream().collect(Collectors.toMap(Function.identity(), ApiTestSupport::fileCheck));
        });
    }

    private static FileDirectory.FileCheck fileCheck(UUID id) {
        if (id.equals(FOREIGN_FILE)) {
            return new FileDirectory.FileCheck(id, "someone-else.pdf", "application/pdf", "was uploaded by someone else");
        }
        if (id.equals(IMAGE_FILE)) {
            return new FileDirectory.FileCheck(id, "photo.png", "image/png", null);
        }
        return new FileDirectory.FileCheck(id, "scan.pdf", "application/pdf", null);
    }


    record TestUser(UUID id, String familyName, String givenName, String email, String department, String role) {

        TestUser(String familyName, String givenName, String email, String department, String role) {
            this(UUID.randomUUID(), familyName, givenName, email, department, role);
        }

        String fullName() {
            return familyName + " " + givenName;
        }
    }

    protected static String bearer(TestUser user) {
        TestJwtIssuer.TokenBuilder token = issuer.token(user.role())
                .subject(user.id())
                .username(user.email().substring(0, user.email().indexOf('@')))
                .email(user.email())
                .name(user.familyName(), user.givenName());
        if (user.department() != null) {
            token.department(user.department());
        }
        return token.bearer();
    }

    /** A unique title, because all tests share one database. */
    protected static String uniqueTitle(String base) {
        return base + " #" + templateCounter.incrementAndGet();
    }

    /** Fields: reason (TEXT, required), purpose (SELECT), term (TERM), evidence (FILE). Steps: ACAD, then FIN. */
    protected long createAcadThenFinTemplate() throws Exception {
        return createTemplate("""
                {"title": "%s", "departmentCode": "ACAD", "audience": "STUDENT", "status": "ACTIVE",
                 "fields": [
                   {"key": "reason", "label": "Reason", "type": "TEXT", "required": true},
                   {"key": "purpose", "label": "Purpose", "type": "SELECT", "options": ["Loan", "Visa"]},
                   {"key": "term", "label": "Term", "type": "TERM"},
                   {"key": "evidence", "label": "Evidence", "type": "FILE", "uploadFolder": "evidence"}
                 ],
                 "steps": [
                   {"departmentCode": "ACAD", "description": "Check the records", "slaDays": 2},
                   {"departmentCode": "FIN", "description": "Settle fees", "slaDays": 3}
                 ]}
                """.formatted(uniqueTitle("Confirmation")));
    }

    protected long createTemplate(String json) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/v1/templates")
                .header(AUTHORIZATION, bearer(ADMIN))
                .contentType(APPLICATION_JSON)
                .content(json)
                .exchange();
        assertThat(result).as(() -> body(result)).hasStatus(HttpStatus.CREATED);
        return json(result).read("$.id", Long.class);
    }

    /** The response body, for assertion messages. */
    protected static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (java.io.UnsupportedEncodingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    protected MvcTestResult submit(TestUser user, long templateId, String valuesJson) {
        return mvc.post().uri("/api/v1/requests")
                .header(AUTHORIZATION, bearer(user))
                .contentType(APPLICATION_JSON)
                .content("{\"templateId\": %d, \"values\": %s}".formatted(templateId, valuesJson))
                .exchange();
    }

    /** Submits a valid request for {@link #createAcadThenFinTemplate()} and returns its id. */
    protected long submitValid(TestUser user, long templateId) throws Exception {
        MvcTestResult result = submit(user, templateId, """
                {"reason": "Scholarship application", "purpose": "Loan"}
                """);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return json(result).read("$.id", Long.class);
    }

    protected MvcTestResult complete(TestUser user, long requestId, int position) {
        return mvc.post().uri("/api/v1/requests/{id}/steps/{position}/complete", requestId, position)
                .header(AUTHORIZATION, bearer(user))
                .contentType(APPLICATION_JSON)
                .content("{\"note\": \"Done\"}")
                .exchange();
    }

    protected DocumentContext detail(TestUser user, long requestId) throws Exception {
        MvcTestResult result = mvc.get().uri("/api/v1/requests/{id}", requestId)
                .header(AUTHORIZATION, bearer(user))
                .exchange();
        assertThat(result).hasStatusOk();
        return json(result);
    }

    protected static DocumentContext json(MvcTestResult result) throws Exception {
        return JsonPath.using(LENIENT_JSON).parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected static List<String> strings(DocumentContext json, String path) {
        return json.read(path);
    }
}
