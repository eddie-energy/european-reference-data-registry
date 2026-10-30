package energy.eddie.s3.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import energy.eddie.s3.models.referencedata.Nation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserTest {
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"NDSF", "COORDINATING_NDSF"})
    void onlyNdsfOrganizationIdsGrantAssignedMaintenance(String role) {
        var ndsfId = UUID.randomUUID();
        var operationalEntityId = UUID.randomUUID();
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("organization", Map.of(
                        "eda", Map.of("id", ndsfId.toString(), "ceeds_role", List.of(role)),
                        "op", Map.of("id", operationalEntityId.toString(), "ceeds_role", List.of("OPERATIONAL_ENTITY"))))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new OrganizationRolesConverter().convert(jwt));

        assertThat(new CurrentUser().ndsfOrganizationIds()).containsExactly(ndsfId);
    }

    @Test
    void coordinatingScopeDoesNotIncludeNationsFromOrdinaryMemberships() {
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user")
                .claim("organization", Map.of(
                        "coordinator", Map.of("ceeds_role", "COORDINATING_NDSF", "ceeds_nations", "AUT"),
                        "ordinary", Map.of("ceeds_role", "NDSF", "ceeds_nations", "DEU")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new OrganizationRolesConverter().convert(jwt));
        var currentUser = new CurrentUser();

        assertThat(currentUser.coordinatingNdsfNations()).containsExactly(Nation.AUT);
        assertThat(currentUser.mayManageResponsibilitiesFor(Nation.AUT)).isTrue();
        assertThat(currentUser.mayManageResponsibilitiesFor(Nation.DEU)).isFalse();
        assertThat(currentUser.mayManageResponsibilitiesFor(null)).isFalse();
        assertThat(currentUser.maySeeDrafts()).isTrue();
        assertThat(currentUser.isOperationalEntity()).isFalse();
    }
}
