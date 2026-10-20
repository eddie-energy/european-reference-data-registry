package energy.eddie.s3.services;

import energy.eddie.s3.exceptions.ConflictException;
import energy.eddie.s3.exceptions.ForbiddenException;
import energy.eddie.s3.exceptions.NotFoundException;
import energy.eddie.s3.generated.model.CreateOrganizationRequest;
import energy.eddie.s3.generated.model.ManagedOrganizationDto;
import energy.eddie.s3.generated.model.Nation;
import energy.eddie.s3.generated.model.OrganizationResponsibilityDto;
import energy.eddie.s3.generated.model.OrganizationRole;
import energy.eddie.s3.generated.model.UpdateOrganizationRequest;
import energy.eddie.s3.models.referencedata.Responsibility;
import energy.eddie.s3.repositories.ResponsibilityRepository;
import energy.eddie.s3.security.CeedsRole;
import energy.eddie.s3.security.CurrentUser;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrganizationManagementService {
    static final Set<String> PROTECTED_ORGANIZATION_ALIASES = Set.of("fhooe");

    private final KeycloakUserDirectory directory;
    private final ResponsibilityRepository responsibilities;
    private final CurrentUser currentUser;

    public OrganizationManagementService(KeycloakUserDirectory directory, ResponsibilityRepository responsibilities,
                                         CurrentUser currentUser) {
        this.directory = directory;
        this.responsibilities = responsibilities;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<ManagedOrganizationDto> list() {
        var organizations = directory.listOrganizationDetails().stream()
                .filter(organization -> currentUser.isOperationalEntity()
                        || organization.roles().stream().anyMatch(CeedsRole::isNdsf))
                .toList();
        Set<UUID> ids = organizations.stream().map(KeycloakUserDirectory.KeycloakOrganization::id)
                .collect(Collectors.toSet());
        var held = responsibilities.findByOrganizationIdIn(ids).stream()
                .filter(item -> currentUser.isOperationalEntity()
                        || currentUser.mayManageResponsibilitiesFor(item.getNation()))
                .collect(Collectors.groupingBy(Responsibility::getOrganizationId,
                        Collectors.mapping(OrganizationManagementService::toDto, Collectors.toList())));
        return organizations.stream()
                .map(organization -> toDto(organization, held.getOrDefault(organization.id(), List.of())))
                .toList();
    }

    public ManagedOrganizationDto create(CreateOrganizationRequest request) {
        var roles = toRoles(request.getRoles(), request.getRole());
        var name = request.getName().trim();
        var alias = alias(name);
        if (alias.isEmpty()) {
            throw new ConflictException("Organization name must contain letters or digits");
        }
        return toDto(directory.createOrganization(name, alias, roles, nationsFor(roles, request.getNations())), List.of());
    }

    @Transactional
    public ManagedOrganizationDto update(UUID organizationId, UpdateOrganizationRequest request) {
        var organization = directory.findOrganization(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization " + organizationId + " not found"));
        if (PROTECTED_ORGANIZATION_ALIASES.contains(organization.alias())) {
            throw new ForbiddenException("This organization cannot be edited");
        }
        var roles = toRoles(request.getRoles(), request.getRole());
        var nations = nationsFor(roles, request.getNations());
        directory.updateOrganization(organizationId, roles, nations);
        if (roles.stream().noneMatch(CeedsRole::isNdsf)) {
            responsibilities.deleteByOrganizationId(organizationId);
        }
        var held = responsibilities.findByOrganizationIdIn(Set.of(organizationId)).stream()
                .map(OrganizationManagementService::toDto)
                .toList();
        return toDto(new KeycloakUserDirectory.KeycloakOrganization(
                organizationId, organization.alias(), organization.name(), roles, nations), held);
    }

    static String alias(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(?:^-+)|(?:-+$)", "");
    }

    private static List<CeedsRole> toRoles(@Nullable Collection<OrganizationRole> roles, @Nullable OrganizationRole role) {
        if (roles != null && !roles.isEmpty()) {
            if (roles.stream().anyMatch(Objects::isNull)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization roles must not contain null");
            }
            if (role != null && (roles.size() != 1 || !roles.contains(role))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide either roles or a single role");
            }
            return roles.stream().map(value -> CeedsRole.valueOf(value.name())).distinct().toList();
        }
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one organization role is required");
        }
        return List.of(CeedsRole.valueOf(role.name()));
    }

    private static List<Nation> nationsFor(List<CeedsRole> roles, @Nullable List<Nation> nations) {
        return roles.stream().anyMatch(CeedsRole::isNdsf) && nations != null
                ? nations.stream().distinct().toList() : List.of();
    }

    private ManagedOrganizationDto toDto(
            KeycloakUserDirectory.KeycloakOrganization organization, List<OrganizationResponsibilityDto> held) {
        return new ManagedOrganizationDto()
                .id(organization.id())
                .alias(organization.alias())
                .name(organization.name())
                .editable(currentUser.isOperationalEntity()
                        && !PROTECTED_ORGANIZATION_ALIASES.contains(organization.alias()))
                .role(organization.roles().size() == 1 ? OrganizationRole.fromValue(organization.roles().getFirst().name()) : null)
                .roles(organization.roles().stream().map(role -> OrganizationRole.fromValue(role.name()))
                        .collect(Collectors.toCollection(java.util.LinkedHashSet::new)))
                .nations(organization.nations())
                .responsibilities(held);
    }

    private static OrganizationResponsibilityDto toDto(Responsibility responsibility) {
        return new OrganizationResponsibilityDto()
                .referenceDataObjectId(responsibility.getReferenceDataObject().getId())
                .nation(Nation.fromValue(responsibility.getNation().name()));
    }
}
