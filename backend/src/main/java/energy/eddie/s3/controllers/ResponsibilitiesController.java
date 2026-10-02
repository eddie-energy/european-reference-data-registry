package energy.eddie.s3.controllers;

import energy.eddie.s3.generated.api.ResponsibilitiesApi;
import energy.eddie.s3.generated.model.AssignResponsibilityRequest;
import energy.eddie.s3.generated.model.EligibleOrganizationDto;
import energy.eddie.s3.generated.model.Nation;
import energy.eddie.s3.generated.model.ResponsibilityDto;
import energy.eddie.s3.services.KeycloakOrganizationDirectory;
import energy.eddie.s3.services.ResponsibilityManagementService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResponsibilitiesController implements ResponsibilitiesApi {
    private final ResponsibilityManagementService service;
    private final KeycloakOrganizationDirectory directory;

    public ResponsibilitiesController(ResponsibilityManagementService service, KeycloakOrganizationDirectory directory) {
        this.service = service;
        this.directory = directory;
    }

    @Override
    public ResponseEntity<List<EligibleOrganizationDto>> listEligibleOrganizations() {
        return ResponseEntity.ok(directory.list());
    }

    @Override
    public ResponseEntity<List<ResponsibilityDto>> listResponsibilities(UUID id) {
        return ResponseEntity.ok(service.list(id));
    }

    @Override
    public ResponseEntity<ResponsibilityDto> assignResponsibility(UUID id, AssignResponsibilityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.assign(id, request));
    }

    @Override
    public ResponseEntity<Void> removeResponsibility(UUID id, UUID organizationId, Nation nation) {
        service.remove(id, organizationId, nation);
        return ResponseEntity.noContent().build();
    }
}
