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
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationManagementService {
    static final Set<String> PROTECTED_ORGANIZATION_ALIASES = Set.of("fhooe");

    private final KeycloakUserDirectory directory;
    private final ResponsibilityRepository responsibilities;

    public OrganizationManagementService(KeycloakUserDirectory directory, ResponsibilityRepository responsibilities) {
        this.directory = directory;
        this.responsibilities = responsibilities;
    }

    @Transactional(readOnly = true)
    public List<ManagedOrganizationDto> list() {
        var organizations = directory.listOrganizationDetails();
        Set<UUID> ids = organizations.stream().map(KeycloakUserDirectory.KeycloakOrganization::id)
                .collect(Collectors.toSet());
        var held = responsibilities.findByOrganizationIdIn(ids).stream()
                .collect(Collectors.groupingBy(Responsibility::getOrganizationId,
                        Collectors.mapping(OrganizationManagementService::toDto, Collectors.toList())));
        return organizations.stream()
                .map(organization -> toDto(organization, held.getOrDefault(organization.id(), List.of())))
                .toList();
    }

    public ManagedOrganizationDto create(CreateOrganizationRequest request) {
        var role = toRole(request.getRole());
        var name = request.getName().trim();
        var alias = alias(name);
        if (alias.isEmpty()) {
            throw new ConflictException("Organization name must contain letters or digits");
        }
        return toDto(directory.createOrganization(name, alias, role, nationsFor(role, request.getNations())), List.of());
    }

    @Transactional
    public ManagedOrganizationDto update(UUID organizationId, UpdateOrganizationRequest request) {
        var organization = directory.findOrganization(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization " + organizationId + " not found"));
        if (PROTECTED_ORGANIZATION_ALIASES.contains(organization.alias())) {
            throw new ForbiddenException("This organization cannot be edited");
        }
        var role = toRole(request.getRole());
        var nations = nationsFor(role, request.getNations());
        directory.updateOrganization(organizationId, role, nations);
        if (role != CeedsRole.NDSF) {
            responsibilities.deleteByOrganizationId(organizationId);
        }
        var held = responsibilities.findByOrganizationIdIn(Set.of(organizationId)).stream()
                .map(OrganizationManagementService::toDto)
                .toList();
        return toDto(new KeycloakUserDirectory.KeycloakOrganization(
                organizationId, organization.alias(), organization.name(), role, nations), held);
    }

    static String alias(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(?:^-+)|(?:-+$)", "");
    }

    private static CeedsRole toRole(OrganizationRole role) {
        return CeedsRole.valueOf(role.name());
    }

    private static List<Nation> nationsFor(CeedsRole role, @Nullable List<Nation> nations) {
        return role == CeedsRole.NDSF && nations != null ? nations.stream().distinct().toList() : List.of();
    }

    private static ManagedOrganizationDto toDto(
            KeycloakUserDirectory.KeycloakOrganization organization, List<OrganizationResponsibilityDto> held) {
        return new ManagedOrganizationDto()
                .id(organization.id())
                .alias(organization.alias())
                .name(organization.name())
                .editable(!PROTECTED_ORGANIZATION_ALIASES.contains(organization.alias()))
                .role(organization.role() == null ? null : OrganizationRole.fromValue(organization.role().name()))
                .nations(organization.nations())
                .responsibilities(held);
    }

    private static OrganizationResponsibilityDto toDto(Responsibility responsibility) {
        return new OrganizationResponsibilityDto()
                .referenceDataObjectId(responsibility.getReferenceDataObject().getId())
                .nation(Nation.fromValue(responsibility.getNation().name()));
    }
}
