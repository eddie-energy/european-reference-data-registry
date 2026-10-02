package energy.eddie.s3.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import energy.eddie.s3.models.referencedata.Nation;
import energy.eddie.s3.repositories.ResponsibilityRepository;
import energy.eddie.s3.security.CurrentUser;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResponsibilityServiceTest {

    @Mock ResponsibilityRepository repository;
    @Mock CurrentUser currentUser;
    @InjectMocks ResponsibilityService service;

    private final UUID objectId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();

    @Test
    void assignedOrganizationCanMaintainNationOutsideKeycloakNations() {
        when(currentUser.ndsfOrganizationIds()).thenReturn(Set.of(organizationId));
        when(repository.existsByReferenceDataObjectIdAndOrganizationIdInAndNation(
                        objectId, Set.of(organizationId), Nation.AUT))
                .thenReturn(true);

        assertThat(service.mayMaintain(objectId, Nation.AUT)).isTrue();
        assertThat(service.mayMaintain(objectId, Nation.GER)).isFalse();
    }

    @Test
    void operationalEntityCanMaintainSharedData() {
        when(currentUser.isOperationalEntity()).thenReturn(true);

        assertThat(service.mayMaintain(objectId, null)).isTrue();
    }

    @Test
    void unassignedNdsfCannotMaintain() {
        when(currentUser.ndsfOrganizationIds()).thenReturn(Set.of(organizationId));

        assertThat(service.mayMaintain(objectId, Nation.AUT)).isFalse();
    }
}
