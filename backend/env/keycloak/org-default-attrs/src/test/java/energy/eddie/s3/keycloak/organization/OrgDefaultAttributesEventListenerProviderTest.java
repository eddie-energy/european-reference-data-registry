package energy.eddie.s3.keycloak.organization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OrganizationModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.organization.OrganizationProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrgDefaultAttributesEventListenerProviderTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private KeycloakContext context;

    @Mock
    private RealmProvider realms;

    @Mock
    private RealmModel previousRealm;

    @Mock
    private RealmModel eventRealm;

    @Mock
    private OrganizationProvider organizations;

    @Mock
    private OrganizationModel organization;

    private OrgDefaultAttributesEventListenerProvider listener;

    @BeforeEach
    void setUp() {
        listener = new OrgDefaultAttributesEventListenerProvider(session);
    }

    @Test
    void createAddsMissingAttributesAndRestoresRealm() {
        arrangeOrganization(Map.of("existing", List.of("value")));

        listener.onEvent(organizationEvent(OperationType.CREATE), false);

        var attributes = captureAttributes();
        assertEquals(List.of(""), attributes.get("ceeds_role"));
        assertEquals(List.of(""), attributes.get("ceeds_nations"));
        assertEquals(List.of("value"), attributes.get("existing"));
        verify(context).setRealm(eventRealm);
        verify(context).setRealm(previousRealm);
    }

    @Test
    void updateRestoresRemovedAttributeWithoutReplacingExistingValue() {
        arrangeOrganization(Map.of("ceeds_role", List.of("OPERATIONAL_ENTITY")));

        listener.onEvent(organizationEvent(OperationType.UPDATE), false);

        var attributes = captureAttributes();
        assertEquals(List.of("OPERATIONAL_ENTITY"), attributes.get("ceeds_role"));
        assertEquals(List.of(""), attributes.get("ceeds_nations"));
    }

    @Test
    void completeAttributesDoNotTriggerUpdate() {
        arrangeOrganization(Map.of(
                "ceeds_role", List.of("NDSF"),
                "ceeds_nations", List.of("AUT")));

        listener.onEvent(organizationEvent(OperationType.UPDATE), false);

        verify(organization, never()).setAttributes(any());
    }

    @Test
    void unrelatedEventDoesNothing() {
        var event = mock(AdminEvent.class);
        when(event.getResourceType()).thenReturn(ResourceType.ORGANIZATION);
        when(event.getOperationType()).thenReturn(OperationType.DELETE);

        listener.onEvent(event, false);

        verify(session, never()).realms();
    }

    @Test
    void missingRealmAndOrganizationExitSafely() {
        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(previousRealm);
        when(session.realms()).thenReturn(realms);
        when(realms.getRealm("ceeds-id")).thenReturn(null, eventRealm);
        when(session.getProvider(OrganizationProvider.class)).thenReturn(organizations);
        when(organizations.getById("org-id")).thenReturn(null);

        assertDoesNotThrow(() -> listener.onEvent(organizationEvent(OperationType.CREATE), false));
        assertDoesNotThrow(() -> listener.onEvent(organizationEvent(OperationType.CREATE), false));

        verify(organization, never()).setAttributes(any());
        verify(context).setRealm(previousRealm);
    }

    private void arrangeOrganization(Map<String, List<String>> attributes) {
        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(previousRealm);
        when(session.realms()).thenReturn(realms);
        when(realms.getRealm("ceeds-id")).thenReturn(eventRealm);
        when(session.getProvider(OrganizationProvider.class)).thenReturn(organizations);
        when(organizations.getById("org-id")).thenReturn(organization);
        when(organization.getAttributes()).thenReturn(attributes);
    }

    private Map<String, List<String>> captureAttributes() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<String>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(organization).setAttributes(captor.capture());
        return captor.getValue();
    }

    private static AdminEvent organizationEvent(OperationType operationType) {
        var event = mock(AdminEvent.class);
        when(event.getResourceType()).thenReturn(ResourceType.ORGANIZATION);
        when(event.getOperationType()).thenReturn(operationType);
        when(event.getResourcePath()).thenReturn("organizations/org-id");
        when(event.getRealmId()).thenReturn("ceeds-id");
        return event;
    }
}
