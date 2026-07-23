package energy.eddie.s3.controllers;

import energy.eddie.s3.generated.api.EntriesApi;
import energy.eddie.s3.generated.model.EntryDto;
import energy.eddie.s3.generated.model.UpsertEntryRequest;
import energy.eddie.s3.services.EntryService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EntryController implements EntriesApi {

    private final EntryService service;

    public EntryController(EntryService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<EntryDto>> listEntries(UUID id, UUID versionId) {
        return ResponseEntity.ok(service.listEntries(id, versionId));
    }

    @Override
    public ResponseEntity<EntryDto> createEntry(UUID id, UUID versionId, UpsertEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createEntry(id, versionId, request));
    }

    @Override
    public ResponseEntity<EntryDto> updateEntry(UUID id, UUID versionId, UUID entryId, UpsertEntryRequest request) {
        return ResponseEntity.ok(service.updateEntry(id, versionId, entryId, request));
    }

    @Override
    public ResponseEntity<Void> deleteEntry(UUID id, UUID entryId) {
        service.deleteEntry(id, entryId);
        return ResponseEntity.noContent().build();
    }
}
