package energy.eddie.s3.keycloak.organization;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.keycloak.events.EventListenerProviderFactory;

class OrgDefaultAttributesEventListenerProviderFactoryTest {

    @Test
    void factoryIsDiscoverableThroughServiceLoader() {
        var discovered = ServiceLoader.load(EventListenerProviderFactory.class).stream()
                .map(ServiceLoader.Provider::get)
                .anyMatch(factory -> OrgDefaultAttributesEventListenerProviderFactory.ID.equals(factory.getId()));

        assertTrue(discovered);
    }
}
