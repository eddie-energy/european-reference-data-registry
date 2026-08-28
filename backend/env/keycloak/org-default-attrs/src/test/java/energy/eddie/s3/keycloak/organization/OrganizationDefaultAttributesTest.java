package energy.eddie.s3.keycloak.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrganizationDefaultAttributesTest {

    @Test
    void missingAttributesReceiveBlankValues() {
        var result = OrganizationDefaultAttributes.addTo(Map.of("existing", List.of("value")));

        assertTrue(result.changed());
        assertEquals(
                Map.of(
                        "existing", List.of("value"),
                        "ceeds_role", List.of(""),
                        "ceeds_nations", List.of("")),
                result.attributes());
    }

    @Test
    void existingValuesRemainUnchanged() {
        var attributes = Map.of(
                "ceeds_role", List.of("NDSF"),
                "ceeds_nations", List.of("AUT", "GER"));

        var result = OrganizationDefaultAttributes.addTo(attributes);

        assertFalse(result.changed());
        assertEquals(attributes, result.attributes());
    }

    @Test
    void emptyListsBecomePersistableBlankValues() {
        var result = OrganizationDefaultAttributes.addTo(Map.of(
                "ceeds_role", List.of(),
                "ceeds_nations", List.of("AUT")));

        assertTrue(result.changed());
        assertEquals(List.of(""), result.attributes().get("ceeds_role"));
        assertEquals(List.of("AUT"), result.attributes().get("ceeds_nations"));
    }
}
