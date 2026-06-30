package energy.eddie.s3.controllers;

import energy.eddie.s3.generated.api.ReferenceDataApi;
import energy.eddie.s3.generated.model.CreateFieldRequest;
import energy.eddie.s3.generated.model.CreateReferenceDataObjectRequest;
import energy.eddie.s3.generated.model.FieldDto;
import energy.eddie.s3.generated.model.ReferenceDataObjectDetail;
import energy.eddie.s3.generated.model.ReferenceDataObjectVersionDetail;
import energy.eddie.s3.generated.model.ReplaceVersionFieldsRequest;
import energy.eddie.s3.services.ReferenceDataObjectService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReferenceDataObjectController implements ReferenceDataApi {

    private final ReferenceDataObjectService service;

    public ReferenceDataObjectController(ReferenceDataObjectService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ReferenceDataObjectDetail> createReferenceDataObject(
            CreateReferenceDataObjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Override
    public ResponseEntity<List<ReferenceDataObjectDetail>> getAllReferenceDataObjects() {
        return ResponseEntity.ok(service.getAll());
    }

    @Override
    public ResponseEntity<ReferenceDataObjectDetail> getReferenceDataObject(UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @Override
    public ResponseEntity<Void> deleteReferenceDataObject(UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ReferenceDataObjectVersionDetail> createVersion(UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createVersion(id));
    }

    @Override
    public ResponseEntity<ReferenceDataObjectVersionDetail> publishVersion(UUID id, UUID versionId) {
        return ResponseEntity.ok(service.publishVersion(id, versionId));
    }

    @Override
    public ResponseEntity<FieldDto> createField(UUID id, UUID versionId, CreateFieldRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createField(id, versionId, request));
    }

    @Override
    public ResponseEntity<ReferenceDataObjectVersionDetail> replaceVersionFields(
            UUID id, UUID versionId, ReplaceVersionFieldsRequest request) {
        return ResponseEntity.ok(service.replaceVersionFields(id, versionId, request));
    }

    @Override
    public ResponseEntity<Void> unlinkField(UUID id, UUID versionId, UUID fieldId) {
        service.unlinkField(id, versionId, fieldId);
        return ResponseEntity.noContent().build();
    }
}
