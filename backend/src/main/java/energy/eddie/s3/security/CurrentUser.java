package energy.eddie.s3.security;

import energy.eddie.s3.models.referencedata.Nation;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public Set<CeedsRole> roles() {
        if (token().isEmpty()) {
            return EnumSet.of(CeedsRole.VIEWER);
        }
        var granted = EnumSet.of(CeedsRole.PARTICIPANT);
        Arrays.stream(CeedsRole.values())
                .filter(role -> role.isNdsf() || role == CeedsRole.OPERATIONAL_ENTITY)
                .filter(role -> hasAuthority(role.authority()))
                .forEach(granted::add);
        return granted;
    }

    public boolean isOperationalEntity() {
        return hasAuthority(CeedsRole.OPERATIONAL_ENTITY.authority());
    }

    public boolean isNdsfFor(@Nullable Nation nation) {
        return nation != null
                && hasAuthority(OrganizationRolesConverter.NDSF_NATION_AUTHORITY_PREFIX + nation.name());
    }

    public boolean isCoordinatingNdsf() {
        return hasAuthority(CeedsRole.COORDINATING_NDSF.authority());
    }

    public boolean mayManageResponsibilitiesFor(@Nullable Nation nation) {
        return isOperationalEntity() || (isCoordinatingNdsf() && nation != null
                && hasAuthority(OrganizationRolesConverter.COORDINATING_NDSF_NATION_AUTHORITY_PREFIX + nation.name()));
    }

    public Set<Nation> coordinatingNdsfNations() {
        var nations = EnumSet.noneOf(Nation.class);
        Arrays.stream(Nation.values())
                .filter(nation -> isCoordinatingNdsf() && hasAuthority(
                        OrganizationRolesConverter.COORDINATING_NDSF_NATION_AUTHORITY_PREFIX + nation.name()))
                .forEach(nations::add);
        return nations;
    }

    public boolean isNdsf() {
        return hasAuthority(CeedsRole.NDSF.authority());
    }

    public boolean maySeeDrafts() {
        return isOperationalEntity() || isNdsf();
    }

    public Set<Nation> ndsfNations() {
        var nations = EnumSet.noneOf(Nation.class);
        Arrays.stream(Nation.values()).filter(this::isNdsfFor).forEach(nations::add);
        return nations;
    }

    public List<String> organizations() {
        return token()
                .map(token -> OrganizationClaim.read(token.getToken()).stream()
                        .map(OrganizationMembership::alias)
                        .sorted()
                        .toList())
                .orElseGet(List::of);
    }

    public Set<UUID> ndsfOrganizationIds() {
        return token()
                .map(token -> OrganizationClaim.read(token.getToken()).stream()
                        .filter(membership -> membership.roles().stream().anyMatch(CeedsRole::isNdsf))
                        .map(OrganizationMembership::id)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet()))
                .orElseGet(Set::of);
    }

    public String username() {
        return token().map(JwtAuthenticationToken::getName).orElse("");
    }

    private boolean hasAuthority(String authority) {
        return token()
                .map(token -> token.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(authority::equals))
                .orElse(false);
    }

    private static Optional<JwtAuthenticationToken> token() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token && token.isAuthenticated()) {
            return Optional.of(token);
        }
        return Optional.empty();
    }
}
