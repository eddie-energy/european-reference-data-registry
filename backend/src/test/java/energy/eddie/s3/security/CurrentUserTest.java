package energy.eddie.s3.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserTest {
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void onlyNdsfOrganizationIdsGrantAssignedMaintenance() {
        var ndsfId = UUID.randomUUID();
        var operationalEntityId = UUID.randomUUID();
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("organization", Map.of(
                        "eda", Map.of("id", ndsfId.toString(), "ceeds_role", List.of("NDSF")),
                        "op", Map.of("id", operationalEntityId.toString(), "ceeds_role", List.of("OPERATIONAL_ENTITY"))))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new OrganizationRolesConverter().convert(jwt));

        assertThat(new CurrentUser().ndsfOrganizationIds()).containsExactly(ndsfId);
    }
}
