package energy.eddie.s3.services;

import energy.eddie.s3.exceptions.ConflictException;
import energy.eddie.s3.exceptions.ForbiddenException;
import energy.eddie.s3.security.CurrentUser;
import energy.eddie.s3.exceptions.NotFoundException;
import energy.eddie.s3.generated.model.AssignResponsibilityRequest;
import energy.eddie.s3.generated.model.ResponsibilityDto;
import energy.eddie.s3.models.referencedata.Responsibility;
import energy.eddie.s3.repositories.ReferenceDataObjectRepository;
import energy.eddie.s3.repositories.ResponsibilityRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResponsibilityManagementService {
    private final ResponsibilityRepository repository;
    private final ReferenceDataObjectRepository objects;
    private final KeycloakOrganizationDirectory directory;
    private final CurrentUser currentUser;

    public ResponsibilityManagementService(
            ResponsibilityRepository repository,
            ReferenceDataObjectRepository objects,
            KeycloakOrganizationDirectory directory,
            CurrentUser currentUser) {
        this.repository = repository;
        this.objects = objects;
        this.directory = directory;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<ResponsibilityDto> list(UUID objectId) {
        requireObject(objectId);
        return repository.findByReferenceDataObjectId(objectId).stream()
                .filter(item -> currentUser.mayManageResponsibilitiesFor(item.getNation()))
                .map(ResponsibilityManagementService::toDto).toList();
    }

    @Transactional
    public ResponsibilityDto assign(UUID objectId, AssignResponsibilityRequest request) {
        var nation = energy.eddie.s3.models.referencedata.Nation.valueOf(request.getNation().name());
        requireManageableNation(nation);
        var object = objects.findById(objectId)
                .orElseThrow(() -> new NotFoundException("Reference data object " + objectId + " not found"));
        if (!directory.isEligible(request.getOrganizationId())) {
            throw new NotFoundException("NDSF organization " + request.getOrganizationId() + " not found");
        }
        if (repository.existsByReferenceDataObjectIdAndOrganizationIdAndNation(
                objectId, request.getOrganizationId(), nation)) {
            throw new ConflictException("Responsibility already exists");
        }
        try {
            return toDto(repository.saveAndFlush(new Responsibility(object, request.getOrganizationId(), nation)));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Responsibility already exists");
        }
    }

    @Transactional
    public void remove(UUID objectId, UUID organizationId, energy.eddie.s3.generated.model.Nation nation) {
        var domainNation = energy.eddie.s3.models.referencedata.Nation.valueOf(nation.name());
        requireManageableNation(domainNation);
        requireObject(objectId);
        if (!repository.existsByReferenceDataObjectIdAndOrganizationIdAndNation(objectId, organizationId, domainNation)) {
            throw new NotFoundException("Responsibility not found");
        }
        repository.deleteByReferenceDataObjectIdAndOrganizationIdAndNation(objectId, organizationId, domainNation);
    }

    private void requireManageableNation(energy.eddie.s3.models.referencedata.Nation nation) {
        if (!currentUser.mayManageResponsibilitiesFor(nation)) {
            throw new ForbiddenException("Your role does not permit managing responsibilities for this nation");
        }
    }

    private void requireObject(UUID objectId) {
        if (!objects.existsById(objectId)) {
            throw new NotFoundException("Reference data object " + objectId + " not found");
        }
    }

    private static ResponsibilityDto toDto(Responsibility responsibility) {
        return new ResponsibilityDto()
                .organizationId(responsibility.getOrganizationId())
                .nation(energy.eddie.s3.generated.model.Nation.fromValue(responsibility.getNation().name()));
    }
}
