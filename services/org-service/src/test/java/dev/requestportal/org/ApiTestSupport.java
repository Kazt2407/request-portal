package dev.requestportal.org;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Shared Spring context for API tests: one PostgreSQL and one Keycloak for the whole test run.
 * Requests carry real access tokens from that Keycloak, so signature, issuer, audience and claim
 * mapping are all exercised exactly as in production.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiTestSupport {

    private static final Configuration LENIENT_JSON =
            Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);

    @Autowired
    protected MockMvcTester mvc;

    @Value("${portal.keycloak.base-url}")
    private String keycloakUrl;

    /** Signs in through the dev client with the user's password, as listed in the realm file. */
    protected String accessToken(String username) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "portal-dev");
        form.add("username", username);
        form.add("password", "password");
        Map<?, ?> response = RestClient.create().post()
                .uri(keycloakUrl + "/realms/request-portal/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        return (String) response.get("access_token");
    }

    protected String bearer(String username) {
        return "Bearer " + accessToken(username);
    }

    protected static DocumentContext claims(String accessToken) {
        String payload = new String(
                Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]), StandardCharsets.UTF_8);
        return JsonPath.using(LENIENT_JSON).parse(payload);
    }

    protected static DocumentContext json(MvcTestResult result) throws Exception {
        return JsonPath.using(LENIENT_JSON)
                .parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
