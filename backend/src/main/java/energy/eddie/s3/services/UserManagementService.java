package energy.eddie.s3.services;

import energy.eddie.s3.exceptions.ForbiddenException;
import energy.eddie.s3.exceptions.NotFoundException;
import energy.eddie.s3.generated.model.CreateManagementUserRequest;
import energy.eddie.s3.generated.model.ManagementUserDto;
import energy.eddie.s3.generated.model.OrganizationRefDto;
import energy.eddie.s3.generated.model.UpdateManagementUserRequest;
import jakarta.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UserManagementService {
    static final Set<String> PROTECTED_USERNAMES = Set.of("ceeds");

    private final KeycloakUserDirectory directory;

    public UserManagementService(KeycloakUserDirectory directory) {
        this.directory = directory;
    }

    public List<ManagementUserDto> list() {
        var organizations = directory.listOrganizations();
        var byUser = directory.organizationsByUser(organizations);
        return directory.listUsers().stream()
                .map(user -> toDto(user, byUser.getOrDefault(user.id(), List.of()).stream().findFirst().orElse(null)))
                .toList();
    }

    public ManagementUserDto create(CreateManagementUserRequest request) {
        var organizations = directory.listOrganizations();
        var organization = requireOrganization(organizations, request.getOrganizationId());
        var userId = directory.createUser(request.getUsername(), request.getTemporaryPassword(), request.getCountry());
        try {
            if (organization != null) {
                directory.addMember(organization.getId(), userId);
            }
        } catch (RuntimeException ex) {
            directory.deleteUser(userId);
            throw ex;
        }
        return toDto(requireUser(userId), organization);
    }

    public ManagementUserDto update(UUID userId, UpdateManagementUserRequest request) {
        if (PROTECTED_USERNAMES.contains(requireUser(userId).username())) {
            throw new ForbiddenException("This user cannot be edited");
        }
        var organizations = directory.listOrganizations();
        var organization = requireOrganization(organizations, request.getOrganizationId());
        directory.updateCountry(userId, request.getCountry());
        Set<UUID> current = new HashSet<>(directory.userOrganizationIds(userId));
        UUID wanted = organization == null ? null : organization.getId();
        if (wanted != null && !current.contains(wanted)) {
            directory.addMember(wanted, userId);
        }
        current.stream().filter(id -> !id.equals(wanted)).forEach(id -> directory.removeMember(id, userId));
        return toDto(requireUser(userId), organization);
    }

    private static @Nullable OrganizationRefDto requireOrganization(
            List<OrganizationRefDto> organizations, @Nullable UUID organizationId) {
        if (organizationId == null) {
            return null;
        }
        return organizations.stream()
                .filter(organization -> organization.getId().equals(organizationId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Organization not found"));
    }

    private KeycloakUserDirectory.KeycloakUser requireUser(UUID userId) {
        return directory.findUser(userId).orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
    }

    private static ManagementUserDto toDto(KeycloakUserDirectory.KeycloakUser user, @Nullable OrganizationRefDto organization) {
        return new ManagementUserDto()
                .id(user.id())
                .username(user.username())
                .editable(!PROTECTED_USERNAMES.contains(user.username()))
                .country(user.country())
                .organization(organization);
    }
}
