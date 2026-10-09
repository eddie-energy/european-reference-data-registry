package energy.eddie.s3.services;

import com.fasterxml.jackson.databind.JsonNode;
import energy.eddie.s3.generated.model.Nation;
import energy.eddie.s3.security.CeedsRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jakarta.annotation.Nullable;

final class KeycloakAttributes {
    static final String ATTRIBUTES = "attributes";
    static final String ROLE = "ceeds_role";
    static final String NATIONS = "ceeds_nations";

    private KeycloakAttributes() {
    }

    static List<String> values(JsonNode node) {
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(item -> values.add(item.asText()));
            return values;
        }
        return node.isTextual() ? List.of(node.asText()) : List.of();
    }

    static List<Nation> nationsOf(JsonNode organization) {
        return values(organization.path(ATTRIBUTES).path(NATIONS)).stream()
                .map(value -> {
                    try {
                        return Nation.fromValue(value);
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    static @Nullable CeedsRole roleOf(JsonNode organization) {
        return values(organization.path(ATTRIBUTES).path(ROLE)).stream()
                .map(CeedsRole::assignable)
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .orElse(null);
    }
}
