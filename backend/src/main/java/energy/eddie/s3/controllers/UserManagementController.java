package energy.eddie.s3.controllers;

import energy.eddie.s3.generated.api.UserManagementApi;
import energy.eddie.s3.generated.model.CreateManagementUserRequest;
import energy.eddie.s3.generated.model.ManagementUserDto;
import energy.eddie.s3.generated.model.UpdateManagementUserRequest;
import energy.eddie.s3.services.UserManagementService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserManagementController implements UserManagementApi {
    private final UserManagementService service;

    public UserManagementController(UserManagementService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<ManagementUserDto>> listManagementUsers() {
        return ResponseEntity.ok(service.list());
    }

    @Override
    public ResponseEntity<ManagementUserDto> createManagementUser(CreateManagementUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Override
    public ResponseEntity<ManagementUserDto> updateManagementUser(UUID userId, UpdateManagementUserRequest request) {
        return ResponseEntity.ok(service.update(userId, request));
    }
}
