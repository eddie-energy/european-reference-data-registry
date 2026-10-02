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
    @InjectMocks ResponsibilityManagementService service;

    private final UUID objectId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();

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
