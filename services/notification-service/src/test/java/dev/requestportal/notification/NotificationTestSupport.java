package dev.requestportal.notification;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import dev.requestportal.notification.TestcontainersConfiguration.MailpitContainer;
import dev.requestportal.notification.org.DepartmentDirectory;
import dev.requestportal.notification.org.DepartmentDirectory.Department;
import dev.requestportal.testsupport.TestJwtIssuer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** One context for all tests: real PostgreSQL, Kafka and Mailpit; org-service replaced by a mock. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
abstract class NotificationTestSupport {

    static final String TOPIC = "request-events";
    private static final TestJwtIssuer issuer = TestJwtIssuer.start();
    private static final AtomicLong requestIds = new AtomicLong(1000);
    private static final Configuration LENIENT_JSON =
            Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);

    @Autowired
    protected KafkaTemplate<String, String> kafka;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected MailpitContainer mailpit;

    @MockitoBean
    protected DepartmentDirectory departments;

    @DynamicPropertySource
    static void useTestIssuer(DynamicPropertyRegistry registry) {
        registry.add("KEYCLOAK_URL", issuer::baseUrl);
        registry.add("KEYCLOAK_ISSUER_URI", () -> TestJwtIssuer.ISSUER);
    }

    @BeforeEach
    void stubDepartments() {
        Map<String, Department> known = Map.of(
                "ACAD", new Department("ACAD", "Academic Affairs Office", "academic-affairs@example.edu"),
                "FIN", new Department("FIN", "Finance and Accounting Office", "finance@example.edu"),
                "STU", new Department("STU", "Student Affairs Office", null));
        given(departments.find(anyString()))
                .willAnswer(invocation -> Optional.ofNullable(known.get(invocation.<String>getArgument(0))));
    }

    /** A token signed by the issuer this service trusts. */
    protected static TestJwtIssuer.TokenBuilder token(String... roles) {
        return issuer.token(roles);
    }

    /** A request id and code no other test uses, since all tests share one database and inbox. */
    protected static Request newRequest(String title) {
        long id = requestIds.incrementAndGet();
        return new Request(id, "ACAD-" + id, title);
    }

    protected record Request(long id, String code, String title) {
    }

    protected String envelope(UUID eventId, String type, Request request, Map<String, Object> data) {
        Map<String, Object> requester = new LinkedHashMap<>();
        requester.put("id", "a1000000-0000-4000-8000-000000000001");
        requester.put("name", "Nguyễn Văn An");
        requester.put("email", "an.nguyen@student.example.edu");
        Map<String, Object> about = new LinkedHashMap<>();
        about.put("id", request.id());
        about.put("code", request.code());
        about.put("title", request.title());
        about.put("departmentCode", "ACAD");
        about.put("requester", requester);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("type", type);
        envelope.put("version", 1);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("request", about);
        envelope.put("data", data);
        return json.writeValueAsString(envelope);
    }

    protected UUID publish(String type, Request request, Map<String, Object> data) throws Exception {
        UUID eventId = UUID.randomUUID();
        kafka.send(TOPIC, String.valueOf(request.id()), envelope(eventId, type, request, data)).get();
        return eventId;
    }

    protected boolean processed(UUID eventId) {
        return jdbc.sql("select count(*) from processed_event where event_id = ?")
                .param(eventId)
                .query(Integer.class)
                .single() == 1;
    }

    /** Messages in Mailpit whose subject contains the text, newest first. */
    protected List<DocumentContext> emailsWithSubject(String text) {
        String list = RestClient.create().get().uri(mailpit.apiUrl() + "/messages?limit=500").retrieve().body(String.class);
        List<Map<String, Object>> messages = JsonPath.using(LENIENT_JSON).parse(list).read("$.messages");
        return messages.stream()
                .filter(message -> String.valueOf(message.get("Subject")).contains(text))
                .map(message -> JsonPath.using(LENIENT_JSON).parse(RestClient.create().get()
                        .uri(mailpit.apiUrl() + "/message/" + message.get("ID"))
                        .retrieve()
                        .body(String.class)))
                .toList();
    }
}
