package energy.eddie.s3.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import energy.eddie.s3.exceptions.ConflictException;
import energy.eddie.s3.exceptions.NotFoundException;
import energy.eddie.s3.generated.model.AssignResponsibilityRequest;
import energy.eddie.s3.generated.model.Nation;
import energy.eddie.s3.models.referencedata.ReferenceDataObject;
import energy.eddie.s3.models.referencedata.Responsibility;
import energy.eddie.s3.repositories.ReferenceDataObjectRepository;
import energy.eddie.s3.repositories.ResponsibilityRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Spy;
import energy.eddie.s3.security.CurrentUser;
import energy.eddie.s3.security.OrganizationRolesConverter;
import energy.eddie.s3.exceptions.ForbiddenException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.Map;
import static org.mockito.Mockito.verifyNoInteractions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ResponsibilityManagementServiceTest {
    @Mock ResponsibilityRepository repository;
    @Mock ReferenceDataObjectRepository objects;
    @Mock KeycloakOrganizationDirectory directory;
    @Spy CurrentUser currentUser = new CurrentUser();
    @InjectMocks ResponsibilityManagementService service;

    private final UUID objectId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void operationalEntity() {
        authenticate("OPERATIONAL_ENTITY", List.of());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String role, List<String> nations) {
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user")
                .claim("organization", Map.of("org", Map.of("ceeds_role", role, "ceeds_nations", nations)))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new OrganizationRolesConverter().convert(jwt));
    }

    @Test
    void coordinatorCanAssignWithinItsCountries() {
        authenticate("COORDINATING_NDSF", List.of("AUT"));
        when(objects.findById(objectId)).thenReturn(Optional.of(new ReferenceDataObject("x", "y")));
        when(directory.isEligible(organizationId)).thenReturn(true);
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.assign(objectId, new AssignResponsibilityRequest(organizationId, Nation.AUT)).getNation())
                .isEqualTo(Nation.AUT);
    }

    @Test
    void coordinatorCannotAssignOrRemoveOutsideItsCountries() {
        authenticate("COORDINATING_NDSF", List.of("AUT"));
        assertThatThrownBy(() -> service.assign(objectId, new AssignResponsibilityRequest(organizationId, Nation.DEU)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.remove(objectId, organizationId, Nation.DEU))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(repository, objects, directory);
    }

    @Test
    void coordinatorOnlyListsItsCountries() {
        authenticate("COORDINATING_NDSF", List.of("AUT"));
        when(objects.existsById(objectId)).thenReturn(true);
        var object = new ReferenceDataObject("x", "y");
        when(repository.findByReferenceDataObjectId(objectId)).thenReturn(List.of(
                new Responsibility(object, organizationId, energy.eddie.s3.models.referencedata.Nation.AUT),
                new Responsibility(object, organizationId, energy.eddie.s3.models.referencedata.Nation.DEU)));
        assertThat(service.list(objectId)).extracting(item -> item.getNation()).containsExactly(Nation.AUT);
    }

    @Test
    void coordinatorCanRemoveWithinItsCountries() {
        authenticate("COORDINATING_NDSF", List.of("AUT"));
        when(objects.existsById(objectId)).thenReturn(true);
        when(repository.existsByReferenceDataObjectIdAndOrganizationIdAndNation(
                objectId, organizationId, energy.eddie.s3.models.referencedata.Nation.AUT)).thenReturn(true);
        service.remove(objectId, organizationId, Nation.AUT);
        verify(repository).deleteByReferenceDataObjectIdAndOrganizationIdAndNation(
                objectId, organizationId, energy.eddie.s3.models.referencedata.Nation.AUT);
    }

    @Test
    void coordinatorWithoutValidCountriesCannotAssign() {
        authenticate("COORDINATING_NDSF", List.of("ATLANTIS"));
        assertThatThrownBy(() -> service.assign(objectId, new AssignResponsibilityRequest(organizationId, Nation.AUT)))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(repository, objects, directory);
    }

    @Test
    void assignsNationEvenWhenKeycloakNationDiffers() {
        var object = new ReferenceDataObject("Permissions", "description");
        ReflectionTestUtils.setField(object, "id", objectId);
        when(objects.findById(objectId)).thenReturn(Optional.of(object));
        when(directory.isEligible(organizationId)).thenReturn(true);
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        var result = service.assign(objectId, new AssignResponsibilityRequest(organizationId, Nation.AUT));

        assertThat(result.getNation()).isEqualTo(Nation.AUT);
        verify(repository).saveAndFlush(any(Responsibility.class));
    }

    @Test
    void rejectsDuplicateAssignment() {
        when(objects.findById(objectId)).thenReturn(Optional.of(new ReferenceDataObject("x", "y")));
        when(directory.isEligible(organizationId)).thenReturn(true);
        when(repository.existsByReferenceDataObjectIdAndOrganizationIdAndNation(
                        objectId, organizationId, energy.eddie.s3.models.referencedata.Nation.AUT))
                .thenReturn(true);

        assertThatThrownBy(() -> service.assign(objectId, new AssignResponsibilityRequest(organizationId, Nation.AUT)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsOrganizationWithoutNdsfRole() {
        when(objects.findById(objectId)).thenReturn(Optional.of(new ReferenceDataObject("x", "y")));

        assertThatThrownBy(() -> service.assign(objectId, new AssignResponsibilityRequest(organizationId, Nation.AUT)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void removeRequiresExistingAssignment() {
        when(objects.existsById(objectId)).thenReturn(true);

        assertThatThrownBy(() -> service.remove(objectId, organizationId, Nation.AUT))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void concurrentDuplicateReturnsConflict() {
        when(objects.findById(objectId)).thenReturn(Optional.of(new ReferenceDataObject("x", "y")));
        when(directory.isEligible(organizationId)).thenReturn(true);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.assign(objectId, new AssignResponsibilityRequest(organizationId, Nation.AUT)))
                .isInstanceOf(ConflictException.class);
    }
}
