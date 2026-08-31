package energy.eddie.s3.controllers;

import energy.eddie.s3.generated.api.ReferenceDataEntriesApi;
import energy.eddie.s3.generated.model.ReferenceDataEntryDto;
import energy.eddie.s3.generated.model.UpsertReferenceDataEntryRequest;
import energy.eddie.s3.services.ReferenceDataEntryService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReferenceDataEntryController implements ReferenceDataEntriesApi {

    private final ReferenceDataEntryService service;

    public ReferenceDataEntryController(ReferenceDataEntryService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<ReferenceDataEntryDto>> listReferenceDataEntries(UUID id, UUID versionId) {
        return ResponseEntity.ok(service.listReferenceDataEntries(id, versionId));
    }

    @Override
    public ResponseEntity<ReferenceDataEntryDto> createReferenceDataEntry(
            UUID id, UUID versionId, UpsertReferenceDataEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createReferenceDataEntry(id, versionId, request));
    }

    @Override
    public ResponseEntity<ReferenceDataEntryDto> updateReferenceDataEntry(
            UUID id,
            UUID versionId,
            UUID referenceDataEntryId,
            UpsertReferenceDataEntryRequest request) {
        return ResponseEntity.ok(
                service.updateReferenceDataEntry(id, versionId, referenceDataEntryId, request));
    }

    @Override
    public ResponseEntity<Void> deleteReferenceDataEntry(UUID id, UUID referenceDataEntryId) {
        service.deleteReferenceDataEntry(id, referenceDataEntryId);
        return ResponseEntity.noContent().build();
    }
}
