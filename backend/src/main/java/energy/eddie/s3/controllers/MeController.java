package energy.eddie.s3.controllers;

import energy.eddie.s3.generated.api.MeApi;
import energy.eddie.s3.generated.model.CurrentUserDto;
import energy.eddie.s3.generated.model.Nation;
import energy.eddie.s3.generated.model.Role;
import energy.eddie.s3.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController implements MeApi {

    private final CurrentUser currentUser;

    public MeController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<CurrentUserDto> getCurrentUser() {
        var dto = new CurrentUserDto()
                .username(currentUser.username())
                .roles(currentUser.roles().stream()
                        .map(role -> Role.fromValue(role.name()))
                        .toList())
                .ndsfNations(currentUser.ndsfNations().stream()
                        .map(nation -> Nation.fromValue(nation.name()))
                        .toList())
                .organizations(currentUser.organizations());
        return ResponseEntity.ok(dto);
    }
}
