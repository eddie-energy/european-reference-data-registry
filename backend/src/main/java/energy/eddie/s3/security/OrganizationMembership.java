package energy.eddie.s3.security;

import energy.eddie.s3.models.referencedata.Nation;
import java.util.Set;

public record OrganizationMembership(String alias, Set<CeedsRole> roles, Set<Nation> ndsfNations) {}
