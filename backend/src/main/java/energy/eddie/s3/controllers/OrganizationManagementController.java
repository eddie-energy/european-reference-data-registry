package energy.eddie.s3.controllers;

import energy.eddie.s3.generated.api.OrganizationManagementApi;
import energy.eddie.s3.generated.model.CreateOrganizationRequest;
import energy.eddie.s3.generated.model.ManagedOrganizationDto;
import energy.eddie.s3.generated.model.UpdateOrganizationRequest;
import energy.eddie.s3.services.OrganizationManagementService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrganizationManagementController implements OrganizationManagementApi {
    private final OrganizationManagementService service;

    public OrganizationManagementController(OrganizationManagementService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<ManagedOrganizationDto>> listAllOrganizations() {
        return ResponseEntity.ok(service.list());
    }

    @Override
    public ResponseEntity<ManagedOrganizationDto> createOrganization(CreateOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Override
    public ResponseEntity<ManagedOrganizationDto> updateOrganization(UUID organizationId, UpdateOrganizationRequest request) {
        return ResponseEntity.ok(service.update(organizationId, request));
    }
}
