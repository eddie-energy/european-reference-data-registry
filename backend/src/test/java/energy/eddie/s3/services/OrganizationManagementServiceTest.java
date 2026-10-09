package energy.eddie.s3.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import energy.eddie.s3.exceptions.ConflictException;
import energy.eddie.s3.exceptions.ForbiddenException;
import energy.eddie.s3.exceptions.NotFoundException;
import energy.eddie.s3.generated.model.CreateOrganizationRequest;
import energy.eddie.s3.generated.model.Nation;
import energy.eddie.s3.generated.model.OrganizationRole;
import energy.eddie.s3.generated.model.UpdateOrganizationRequest;
import energy.eddie.s3.models.referencedata.ReferenceDataObject;
import energy.eddie.s3.models.referencedata.Responsibility;
import energy.eddie.s3.repositories.ResponsibilityRepository;
import energy.eddie.s3.security.CeedsRole;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrganizationManagementServiceTest {
    @Mock KeycloakUserDirectory directory;
    @Mock ResponsibilityRepository responsibilities;
    @InjectMocks OrganizationManagementService service;

    private final UUID organizationId = UUID.randomUUID();

    @Test
    void listIncludesRoleNationsAndResponsibilities() {
        var objectId = UUID.randomUUID();
        var object = new ReferenceDataObject("Permissions", "description");
        ReflectionTestUtils.setField(object, "id", objectId);
        when(directory.listOrganizationDetails()).thenReturn(List.of(
                organization("eda", CeedsRole.NDSF, Nation.AUT, Nation.GER),
                new KeycloakUserDirectory.KeycloakOrganization(UUID.randomUUID(), "fhooe", "FHOOE", CeedsRole.OPERATIONAL_ENTITY, List.of())));
        when(responsibilities.findByOrganizationIdIn(any())).thenReturn(List.of(
                new Responsibility(object, organizationId, energy.eddie.s3.models.referencedata.Nation.GER)));

        var result = service.list();

        assertThat(result.get(0).getNations()).containsExactly(Nation.AUT, Nation.GER);
        assertThat(result.get(0).getRole()).isEqualTo(OrganizationRole.NDSF);
        assertThat(result.get(0).getEditable()).isTrue();
        assertThat(result.get(0).getResponsibilities()).singleElement().satisfies(held -> {
            assertThat(held.getReferenceDataObjectId()).isEqualTo(objectId);
            assertThat(held.getNation()).isEqualTo(Nation.GER);
        });
        assertThat(result.get(1).getEditable()).isFalse();
    }

    @Test
    void createDerivesAliasAndKeepsNationsForNdsf() {
        when(directory.createOrganization("Energy Agency GmbH", "energy-agency-gmbh", CeedsRole.NDSF, List.of(Nation.AUT, Nation.FRA)))
                .thenReturn(organization("energy-agency-gmbh", CeedsRole.NDSF, Nation.AUT, Nation.FRA));

        var result = service.create(new CreateOrganizationRequest()
                .name(" Energy Agency GmbH ")
                .role(OrganizationRole.NDSF)
                .nations(List.of(Nation.AUT, Nation.AUT, Nation.FRA)));

        assertThat(result.getNations()).containsExactly(Nation.AUT, Nation.FRA);
    }

    @Test
    void createDropsNationsForOperationalEntity() {
        when(directory.createOrganization("Ops", "ops", CeedsRole.OPERATIONAL_ENTITY, List.of()))
                .thenReturn(organization("ops", CeedsRole.OPERATIONAL_ENTITY));

        service.create(new CreateOrganizationRequest()
                .name("Ops")
                .role(OrganizationRole.OPERATIONAL_ENTITY)
                .nations(List.of(Nation.GER)));

        verify(directory).createOrganization("Ops", "ops", CeedsRole.OPERATIONAL_ENTITY, List.of());
    }

    @Test
    void createRejectsNameWithoutLettersOrDigits() {
        var request = new CreateOrganizationRequest().name("---").role(OrganizationRole.NDSF);
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateChangesNationsAndKeepsResponsibilitiesForNdsf() {
        when(directory.findOrganization(organizationId)).thenReturn(Optional.of(organization("eda", CeedsRole.NDSF, Nation.AUT)));
        when(responsibilities.findByOrganizationIdIn(Set.of(organizationId))).thenReturn(List.of());

        var result = service.update(organizationId, new UpdateOrganizationRequest()
                .role(OrganizationRole.NDSF)
                .nations(List.of(Nation.AUT, Nation.ESP)));

        verify(directory).updateOrganization(organizationId, CeedsRole.NDSF, List.of(Nation.AUT, Nation.ESP));
        verify(responsibilities, never()).deleteByOrganizationId(any());
        assertThat(result.getNations()).containsExactly(Nation.AUT, Nation.ESP);
    }

    @Test
    void updateAwayFromNdsfDeletesResponsibilities() {
        when(directory.findOrganization(organizationId)).thenReturn(Optional.of(organization("eda", CeedsRole.NDSF, Nation.AUT)));
        when(responsibilities.findByOrganizationIdIn(Set.of(organizationId))).thenReturn(List.of());

        var result = service.update(organizationId, new UpdateOrganizationRequest()
                .role(OrganizationRole.OPERATIONAL_ENTITY)
                .nations(List.of(Nation.AUT)));

        verify(directory).updateOrganization(organizationId, CeedsRole.OPERATIONAL_ENTITY, List.of());
        verify(responsibilities).deleteByOrganizationId(organizationId);
        assertThat(result.getNations()).isEmpty();
    }

    @Test
    void updateRejectsProtectedOrganization() {
        when(directory.findOrganization(organizationId)).thenReturn(Optional.of(organization("fhooe", CeedsRole.OPERATIONAL_ENTITY)));

        var request = new UpdateOrganizationRequest().role(OrganizationRole.NDSF);
        assertThatThrownBy(() -> service.update(organizationId, request))
                .isInstanceOf(ForbiddenException.class);
        verify(directory, never()).updateOrganization(any(), any(), any());
    }

    @Test
    void updateRejectsUnknownOrganization() {
        when(directory.findOrganization(organizationId)).thenReturn(Optional.empty());

        var request = new UpdateOrganizationRequest().role(OrganizationRole.NDSF);
        assertThatThrownBy(() -> service.update(organizationId, request))
                .isInstanceOf(NotFoundException.class);
    }

    private KeycloakUserDirectory.KeycloakOrganization organization(String alias, CeedsRole role, Nation... nations) {
        return new KeycloakUserDirectory.KeycloakOrganization(organizationId, alias, alias, role, List.of(nations));
    }
}
