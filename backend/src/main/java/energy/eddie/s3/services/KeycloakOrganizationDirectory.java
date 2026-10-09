package energy.eddie.s3.services;

import com.fasterxml.jackson.databind.JsonNode;
import energy.eddie.s3.generated.model.EligibleOrganizationDto;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class KeycloakOrganizationDirectory {
    private final KeycloakAdminClient client;

    public KeycloakOrganizationDirectory(KeycloakAdminClient client) {
        this.client = client;
    }

    public List<EligibleOrganizationDto> list() {
        return client.getAll("/organizations?briefRepresentation=false").stream()
                .filter(KeycloakOrganizationDirectory::eligible)
                .map(KeycloakOrganizationDirectory::toDto)
                .toList();
    }

    public boolean isEligible(UUID organizationId) {
        return client.find("/organizations/" + organizationId)
                .map(KeycloakOrganizationDirectory::eligible)
                .orElse(false);
    }

    private static boolean eligible(JsonNode node) {
        return node.path("enabled").asBoolean(true) && KeycloakAttributes.values(node.path("attributes").path("ceeds_role"))
                .contains("NDSF");
    }

    private static EligibleOrganizationDto toDto(JsonNode node) {
        return new EligibleOrganizationDto()
                .id(UUID.fromString(node.path("id").asText()))
                .alias(node.path("alias").asText())
                .name(node.path("name").asText())
                .keycloakNations(KeycloakAttributes.nationsOf(node));
    }
}
