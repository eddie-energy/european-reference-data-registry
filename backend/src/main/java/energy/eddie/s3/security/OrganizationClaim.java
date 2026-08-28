package energy.eddie.s3.security;

import energy.eddie.s3.models.referencedata.Nation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;

public final class OrganizationClaim {

    public static final String CLAIM = "organization";
    public static final String ROLE_ATTRIBUTE = "ceeds_role";
    public static final String NATIONS_ATTRIBUTE = "ceeds_nations";

    private OrganizationClaim() {}

    public static List<OrganizationMembership> read(Jwt jwt) {
        var claim = jwt.getClaim(CLAIM);
        if (claim instanceof Map<?, ?> byAlias) {
            List<OrganizationMembership> memberships = new ArrayList<>();
            byAlias.forEach((alias, attributes) -> memberships.add(membership(String.valueOf(alias), attributes)));
            return memberships;
        }
        if (claim instanceof Collection<?> aliases) {
            return aliases.stream()
                    .map(alias -> new OrganizationMembership(String.valueOf(alias), Set.of(), Set.of()))
                    .toList();
        }
        return List.of();
    }

    private static OrganizationMembership membership(String alias, @Nullable Object attributes) {
        if (!(attributes instanceof Map<?, ?> map)) {
            return new OrganizationMembership(alias, Set.of(), Set.of());
        }
        Set<CeedsRole> roles = new LinkedHashSet<>();
        for (var value : values(map.get(ROLE_ATTRIBUTE))) {
            CeedsRole.assignable(value).ifPresent(roles::add);
        }
        Set<Nation> nations = new LinkedHashSet<>();
        if (roles.contains(CeedsRole.NDSF)) {
            for (var value : values(map.get(NATIONS_ATTRIBUTE))) {
                nation(value).ifPresent(nations::add);
            }
        }
        return new OrganizationMembership(alias, roles, nations);
    }

    private static List<String> values(@Nullable Object attribute) {
        if (attribute instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        if (attribute instanceof String single && !single.isBlank()) {
            return List.of(single);
        }
        return List.of();
    }

    private static Optional<Nation> nation(String value) {
        try {
            return Optional.of(Nation.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
