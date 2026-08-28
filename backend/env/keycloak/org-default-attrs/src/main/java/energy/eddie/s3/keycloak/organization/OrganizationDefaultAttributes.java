package energy.eddie.s3.keycloak.organization;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class OrganizationDefaultAttributes {

    private static final Map<String, List<String>> DEFAULTS = Map.of(
            "ceeds_role", List.of(""),
            "ceeds_nations", List.of(""));

    private OrganizationDefaultAttributes() {}

    static Result addTo(Map<String, List<String>> existing) {
        var attributes = new HashMap<>(existing);
        boolean changed = false;
        for (var defaultAttribute : DEFAULTS.entrySet()) {
            var values = attributes.get(defaultAttribute.getKey());
            if (values == null || values.isEmpty()) {
                attributes.put(defaultAttribute.getKey(), defaultAttribute.getValue());
                changed = true;
            }
        }
        return new Result(attributes, changed);
    }

    record Result(Map<String, List<String>> attributes, boolean changed) {}
}
