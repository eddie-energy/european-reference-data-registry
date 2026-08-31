package energy.eddie.s3.security;

import energy.eddie.s3.models.referencedata.Nation;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
                .filter(role -> role == CeedsRole.NDSF || role == CeedsRole.OPERATIONAL_ENTITY)
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

    public boolean mayMaintainReferenceDataEntriesFor(@Nullable Nation nation) {
        return isOperationalEntity() || isNdsfFor(nation);
    }

    public boolean isNdsf() {
        return hasAuthority(CeedsRole.NDSF.authority());
    }

    public boolean maySeeDrafts() {
        return isOperationalEntity() || isNdsf();
    }

    public boolean mayMaintainFieldsFor(@Nullable Nation nation) {
        return isOperationalEntity() || isNdsfFor(nation);
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
