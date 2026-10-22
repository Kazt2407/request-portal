package dev.requestportal.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserTests {

    private static final UUID USER_ID = UUID.fromString("56520a65-8b32-41a3-8668-dd6103218651");

    @Test
    void readsIdentityRolesAndDepartmentFromToken() {
        Jwt jwt = token()
                .claim("roles", List.of("STAFF", "default-roles-request-portal", "offline_access"))
                .claim("department", "FIN")
                .build();

        CurrentUser user = CurrentUser.from(jwt);

        assertThat(user.id()).isEqualTo(USER_ID);
        assertThat(user.username()).isEqualTo("staff.finance");
        assertThat(user.email()).isEqualTo("dung.pham@example.edu");
        assertThat(user.roles()).containsExactly(Role.STAFF);
        assertThat(user.departmentCode()).isEqualTo("FIN");
    }

    @Test
    void buildsVietnameseFullNameWithFamilyNameFirst() {
        CurrentUser user = CurrentUser.from(token().build());

        assertThat(user.fullName()).isEqualTo("Phạm Thị Dung");
    }

    @Test
    void handlesTokensWithoutRolesOrDepartment() {
        CurrentUser user = CurrentUser.from(token().claims(claims -> {
            claims.remove("family_name");
            claims.remove("given_name");
        }).build());

        assertThat(user.roles()).isEmpty();
        assertThat(user.departmentCode()).isNull();
        assertThat(user.fullName()).isEqualTo("Thị Dung Phạm");
    }

    @Test
    void convertsOnlyKnownRolesToAuthorities() {
        Jwt jwt = token().claim("roles", List.of("ADMIN", "STAFF", "uma_authorization")).build();

        List<String> authorities = new RolesClaimConverter().convert(jwt).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_STAFF");
    }

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(USER_ID.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("preferred_username", "staff.finance")
                .claim("email", "dung.pham@example.edu")
                .claim("name", "Thị Dung Phạm")
                .claim("given_name", "Thị Dung")
                .claim("family_name", "Phạm");
    }
}
