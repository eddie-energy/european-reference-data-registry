package energy.eddie.s3.services;

import energy.eddie.s3.models.referencedata.Nation;
import energy.eddie.s3.repositories.ResponsibilityRepository;
import energy.eddie.s3.security.CurrentUser;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.springframework.stereotype.Service;

@Service
public class ResponsibilityService {
    private final ResponsibilityRepository repository;
    private final CurrentUser currentUser;

    public ResponsibilityService(ResponsibilityRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    public boolean mayMaintain(UUID objectId, @Nullable Nation nation) {
        if (currentUser.isOperationalEntity()) {
            return true;
        }
        var organizationIds = currentUser.ndsfOrganizationIds();
        return nation != null
                && !organizationIds.isEmpty()
                && repository.existsByReferenceDataObjectIdAndOrganizationIdInAndNation(
                        objectId, organizationIds, nation);
    }

    public Map<UUID, Set<Nation>> mine() {
        var organizationIds = currentUser.ndsfOrganizationIds();
        if (organizationIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByOrganizationIdIn(organizationIds).stream()
                .collect(Collectors.groupingBy(
                        responsibility -> responsibility.getReferenceDataObject().getId(),
                        Collectors.mapping(responsibility -> responsibility.getNation(), Collectors.toSet())));
    }
}
