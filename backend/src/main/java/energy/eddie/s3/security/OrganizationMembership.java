package energy.eddie.s3.security;

import energy.eddie.s3.models.referencedata.Nation;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

public record OrganizationMembership(String alias, @Nullable UUID id, Set<CeedsRole> roles, Set<Nation> ndsfNations) {
    public OrganizationMembership(String alias, Set<CeedsRole> roles, Set<Nation> ndsfNations) {
        this(alias, null, roles, ndsfNations);
    }
}
