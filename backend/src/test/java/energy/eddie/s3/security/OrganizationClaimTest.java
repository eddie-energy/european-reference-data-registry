package energy.eddie.s3.security;

import static org.assertj.core.api.Assertions.assertThat;

import energy.eddie.s3.models.referencedata.Nation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class OrganizationClaimTest {

    private static Jwt jwtWith(Object organizationClaim) {
        var builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("subject")
                .claim("preferred_username", "ceeds")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (organizationClaim != null) {
            builder.claim(OrganizationClaim.CLAIM, organizationClaim);
        }
        return builder.build();
    }

    @Test
    void objectClaimWithIdAndAttributes_yieldsRoleAndNations() {
        var organizationId = UUID.randomUUID();
        var jwt = jwtWith(Map.of(
                "fhooe",
                Map.of("id", organizationId.toString(), "ceeds_role", List.of("NDSF"), "ceeds_nations", List.of("AUT", "DEU"))));

        assertThat(OrganizationClaim.read(jwt))
                .containsExactly(
                        new OrganizationMembership("fhooe", organizationId, Set.of(CeedsRole.NDSF), Set.of(Nation.AUT, Nation.DEU)));
    }

    @Test
    void singleAttributeValueSerializedAsString_isRead() {
        var jwt = jwtWith(Map.of("fhooe", Map.of("ceeds_role", "NDSF", "ceeds_nations", "AUT")));

        assertThat(OrganizationClaim.read(jwt).getFirst().ndsfNations()).containsExactly(Nation.AUT);
    }

    @Test
    void listShapedClaim_yieldsMembershipWithoutRoles() {
        var jwt = jwtWith(List.of("fhooe"));

        assertThat(OrganizationClaim.read(jwt))
                .containsExactly(new OrganizationMembership("fhooe", Set.of(), Set.of()));
    }

    @Test
    void missingClaim_yieldsNoMemberships() {
        assertThat(OrganizationClaim.read(jwtWith(null))).isEmpty();
    }

    @Test
    void unknownRoleAndNationValues_areIgnored() {
        var jwt = jwtWith(Map.of(
                "fhooe",
                Map.of("ceeds_role", List.of("PARTICIPANT", "NONSENSE"), "ceeds_nations", List.of("ATLANTIS"))));

        var membership = OrganizationClaim.read(jwt).getFirst();
        assertThat(membership.roles()).isEmpty();
        assertThat(membership.ndsfNations()).isEmpty();
    }

    @Test
    void nationsWithoutTheNdsfRole_areIgnored() {
        var jwt = jwtWith(
                Map.of("fhooe", Map.of("ceeds_role", List.of("OPERATIONAL_ENTITY"), "ceeds_nations", List.of("AUT"))));

        var membership = OrganizationClaim.read(jwt).getFirst();
        assertThat(membership.roles()).containsExactly(CeedsRole.OPERATIONAL_ENTITY);
        assertThat(membership.ndsfNations()).isEmpty();
    }

    @Test
    void severalOrganizations_areUnioned() {
        var jwt = jwtWith(Map.of(
                "fhooe",
                Map.of("ceeds_role", List.of("NDSF"), "ceeds_nations", List.of("AUT")),
                "other",
                Map.of("ceeds_role", List.of("NDSF"), "ceeds_nations", List.of("DEU"))));

        var authorities = new OrganizationRolesConverter().convert(jwt).getAuthorities().stream()
                .map(Object::toString)
                .toList();

        assertThat(authorities)
                .containsExactlyInAnyOrder("ROLE_PARTICIPANT", "ROLE_NDSF", "NDSF_NATION_AUT", "NDSF_NATION_DEU");
    }

    @Test
    void coordinatorInheritsNdsfButOnlyCoordinatesItsOwnMembershipNations() {
        var jwt = jwtWith(Map.of(
                "coordinator", Map.of("ceeds_role", "COORDINATING_NDSF", "ceeds_nations", List.of("AUT", "ATLANTIS")),
                "ordinary", Map.of("ceeds_role", "NDSF", "ceeds_nations", "DEU"),
                "second", Map.of("ceeds_role", "COORDINATING_NDSF", "ceeds_nations", "FRA")));
        var authorities = new OrganizationRolesConverter().convert(jwt).getAuthorities().stream()
                .map(Object::toString).toList();
        assertThat(authorities).containsExactlyInAnyOrder(
                "ROLE_PARTICIPANT", "ROLE_NDSF", "ROLE_COORDINATING_NDSF",
                "NDSF_NATION_AUT", "NDSF_NATION_DEU", "NDSF_NATION_FRA",
                "COORDINATING_NDSF_NATION_AUT", "COORDINATING_NDSF_NATION_FRA");
    }

    @Test
    void everyTokenGrantsParticipant() {
        var authorities = new OrganizationRolesConverter().convert(jwtWith(null)).getAuthorities().stream()
                .map(Object::toString)
                .toList();

        assertThat(authorities).containsExactly("ROLE_PARTICIPANT");
    }
}
