package energy.eddie.s3.controllers;

import energy.eddie.s3.generated.api.MeApi;
import energy.eddie.s3.generated.model.CurrentUserDto;
import energy.eddie.s3.generated.model.Nation;
import energy.eddie.s3.generated.model.MyResponsibilityDto;
import energy.eddie.s3.generated.model.Role;
import energy.eddie.s3.security.CurrentUser;
import energy.eddie.s3.services.ResponsibilityService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController implements MeApi {

    private final CurrentUser currentUser;
    private final ResponsibilityService responsibilityService;

    public MeController(CurrentUser currentUser, ResponsibilityService responsibilityService) {
        this.currentUser = currentUser;
        this.responsibilityService = responsibilityService;
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

    @Override
    public ResponseEntity<List<MyResponsibilityDto>> getMyResponsibilities() {
        return ResponseEntity.ok(responsibilityService.mine().entrySet().stream()
                .map(entry -> new MyResponsibilityDto()
                        .referenceDataObjectId(entry.getKey())
                        .nations(entry.getValue().stream()
                                .map(nation -> Nation.fromValue(nation.name()))
                                .toList()))
                .toList());
    }
}
