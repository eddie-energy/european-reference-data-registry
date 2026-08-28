package energy.eddie.s3.keycloak.organization;

import java.util.EnumSet;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.organization.OrganizationProvider;

public final class OrgDefaultAttributesEventListenerProvider implements EventListenerProvider {

    private static final Logger LOG = Logger.getLogger(OrgDefaultAttributesEventListenerProvider.class);
    private static final EnumSet<OperationType> SUPPORTED_OPERATIONS =
            EnumSet.of(OperationType.CREATE, OperationType.UPDATE);
    private static final String ORGANIZATION_PATH = "organizations/";

    private final KeycloakSession session;

    public OrgDefaultAttributesEventListenerProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {}

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        if (event.getResourceType() != ResourceType.ORGANIZATION
                || !SUPPORTED_OPERATIONS.contains(event.getOperationType())) {
            return;
        }
        String organizationId = organizationId(event.getResourcePath());
        if (organizationId == null) {
            return;
        }
        var realm = session.realms().getRealm(event.getRealmId());
        if (realm == null) {
            LOG.warnf("Could not resolve realm %s for organization %s", event.getRealmId(), organizationId);
            return;
        }
        var context = session.getContext();
        var previousRealm = context.getRealm();
        context.setRealm(realm);
        try {
            var provider = session.getProvider(OrganizationProvider.class);
            var organization = provider.getById(organizationId);
            if (organization == null) {
                LOG.warnf("Organization %s not found after admin event", organizationId);
                return;
            }
            var defaults = OrganizationDefaultAttributes.addTo(organization.getAttributes());
            if (defaults.changed()) {
                organization.setAttributes(defaults.attributes());
                LOG.infof("Applied default attributes to organization %s (%s)", organization.getName(), organizationId);
            }
        } finally {
            context.setRealm(previousRealm);
        }
    }

    private static String organizationId(String resourcePath) {
        if (resourcePath == null || !resourcePath.startsWith(ORGANIZATION_PATH)) {
            return null;
        }
        String organizationId = resourcePath.substring(ORGANIZATION_PATH.length());
        if (organizationId.isBlank() || organizationId.contains("/")) {
            return null;
        }
        return organizationId;
    }

    @Override
    public void close() {}
}
