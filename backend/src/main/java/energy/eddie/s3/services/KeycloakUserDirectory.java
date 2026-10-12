package energy.eddie.s3.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import energy.eddie.s3.exceptions.NotFoundException;
import energy.eddie.s3.generated.model.Nation;
import energy.eddie.s3.generated.model.OrganizationRefDto;
import energy.eddie.s3.security.CeedsRole;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;

@Service
public class KeycloakUserDirectory {
    static final String COUNTRY_ATTRIBUTE = "ceeds_country";
    private static final String ORGANIZATIONS_PATH = "/organizations";
    private static final String USERS_PATH = "/users";
    private static final String ALIAS = "alias";
    private static final String ENABLED = "enabled";

    private final KeycloakAdminClient client;
    private final ObjectMapper mapper;

    public KeycloakUserDirectory(KeycloakAdminClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public record KeycloakUser(UUID id, String username, @Nullable Nation country) {
    }

    public record KeycloakOrganization(
            UUID id, String alias, String name, @Nullable CeedsRole role, List<Nation> nations) {
    }

    public List<KeycloakUser> listUsers() {
        return client.getAll("/users?briefRepresentation=false").stream()
                .map(KeycloakUserDirectory::toUser)
                .toList();
    }

    public Optional<KeycloakUser> findUser(UUID userId) {
        return client.find(USERS_PATH + "/" + userId).map(KeycloakUserDirectory::toUser);
    }

    public List<OrganizationRefDto> listOrganizations() {
        return client.getAll(ORGANIZATIONS_PATH).stream()
                .filter(node -> node.path(ENABLED).asBoolean(true))
                .map(KeycloakUserDirectory::toOrganization)
                .toList();
    }

    public List<KeycloakOrganization> listOrganizationDetails() {
        return client.getAll(ORGANIZATIONS_PATH + "?briefRepresentation=false").stream()
                .filter(node -> node.path(ENABLED).asBoolean(true))
                .map(KeycloakUserDirectory::toOrganizationDetails)
                .toList();
    }

    public Optional<KeycloakOrganization> findOrganization(UUID organizationId) {
        return client.find(ORGANIZATIONS_PATH + "/" + organizationId).map(KeycloakUserDirectory::toOrganizationDetails);
    }

    public KeycloakOrganization createOrganization(String name, String alias, CeedsRole role, List<Nation> nations) {
        var organization = mapper.createObjectNode();
        organization.put("name", name);
        organization.put(ALIAS, alias);
        organization.put(ENABLED, true);
        writeRole(organization.putObject(KeycloakAttributes.ATTRIBUTES), role, nations);
        var location = client.postForLocation(ORGANIZATIONS_PATH, organization);
        return new KeycloakOrganization(
                UUID.fromString(location.substring(location.lastIndexOf('/') + 1)), alias, name, role, nations);
    }

    public void updateOrganization(UUID organizationId, CeedsRole role, List<Nation> nations) {
        var organization = client.get(ORGANIZATIONS_PATH + "/" + organizationId);
        if (!(organization instanceof ObjectNode representation)) {
            throw new NotFoundException("Organization " + organizationId + " not found");
        }
        writeRole(representation.withObjectProperty(KeycloakAttributes.ATTRIBUTES), role, nations);
        client.put(ORGANIZATIONS_PATH + "/" + organizationId, representation);
    }

    public Map<UUID, List<OrganizationRefDto>> organizationsByUser(List<OrganizationRefDto> organizations) {
        Map<UUID, List<OrganizationRefDto>> result = new HashMap<>();
        for (var organization : organizations) {
            for (var member : client.getAll(ORGANIZATIONS_PATH + "/" + organization.getId() + "/members")) {
                var userId = UUID.fromString(member.path("id").asText());
                result.computeIfAbsent(userId, id -> new ArrayList<>()).add(organization);
            }
        }
        return result;
    }

    public List<UUID> userOrganizationIds(UUID userId) {
        List<UUID> ids = new ArrayList<>();
        client.get("/organizations/members/" + userId + ORGANIZATIONS_PATH)
                .forEach(node -> ids.add(UUID.fromString(node.path("id").asText())));
        return ids;
    }

    public void updateCountry(UUID userId, @Nullable Nation country) {
        var user = client.get(USERS_PATH + "/" + userId);
        if (!(user instanceof ObjectNode representation)) {
            throw new NotFoundException("User " + userId + " not found");
        }
        var attributes = representation.withObjectProperty(KeycloakAttributes.ATTRIBUTES);
        if (country == null) {
            attributes.remove(COUNTRY_ATTRIBUTE);
        } else {
            ArrayNode values = attributes.putArray(COUNTRY_ATTRIBUTE);
            values.add(country.getValue());
        }
        client.put(USERS_PATH + "/" + userId, representation);
    }

    public UUID createUser(String username, String temporaryPassword, @Nullable Nation country) {
        var user = mapper.createObjectNode();
        user.put("username", username);
        user.put(ENABLED, true);
        if (country != null) {
            user.putObject(KeycloakAttributes.ATTRIBUTES).putArray(COUNTRY_ATTRIBUTE).add(country.getValue());
        }
        user.putArray("credentials").addObject()
                .put("type", "password")
                .put("value", temporaryPassword)
                .put("temporary", true);
        var location = client.postForLocation(USERS_PATH, user);
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    public void deleteUser(UUID userId) {
        client.delete(USERS_PATH + "/" + userId);
    }

    public void addMember(UUID organizationId, UUID userId) {
        client.post(ORGANIZATIONS_PATH + "/" + organizationId + "/members", userId.toString());
    }

    public void removeMember(UUID organizationId, UUID userId) {
        client.delete(ORGANIZATIONS_PATH + "/" + organizationId + "/members/" + userId);
    }

    private static KeycloakUser toUser(JsonNode node) {
        return new KeycloakUser(
                UUID.fromString(node.path("id").asText()),
                node.path("username").asText(),
                country(node.path(KeycloakAttributes.ATTRIBUTES).path(COUNTRY_ATTRIBUTE)));
    }

    private static @Nullable Nation country(JsonNode values) {
        var text = values.isArray() ? values.path(0).asText("") : values.asText("");
        try {
            return text.isBlank() ? null : Nation.fromValue(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void writeRole(ObjectNode attributes, CeedsRole role, List<Nation> nations) {
        attributes.putArray(KeycloakAttributes.ROLE).add(role.name());
        var values = attributes.putArray(KeycloakAttributes.NATIONS);
        nations.forEach(nation -> values.add(nation.getValue()));
    }

    private static KeycloakOrganization toOrganizationDetails(JsonNode node) {
        return new KeycloakOrganization(
                UUID.fromString(node.path("id").asText()),
                node.path(ALIAS).asText(),
                node.path("name").asText(),
                KeycloakAttributes.roleOf(node),
                KeycloakAttributes.nationsOf(node));
    }

    private static OrganizationRefDto toOrganization(JsonNode node) {
        return new OrganizationRefDto()
                .id(UUID.fromString(node.path("id").asText()))
                .alias(node.path(ALIAS).asText())
                .name(node.path("name").asText());
    }
}
