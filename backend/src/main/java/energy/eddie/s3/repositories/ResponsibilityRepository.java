package energy.eddie.s3.repositories;

import energy.eddie.s3.models.referencedata.Nation;
import energy.eddie.s3.models.referencedata.Responsibility;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResponsibilityRepository extends JpaRepository<Responsibility, UUID> {
    boolean existsByReferenceDataObjectIdAndOrganizationIdInAndNation(UUID objectId, Set<UUID> organizationIds, Nation nation);

    boolean existsByReferenceDataObjectIdAndOrganizationIdAndNation(UUID objectId, UUID organizationId, Nation nation);

    List<Responsibility> findByReferenceDataObjectId(UUID objectId);

    List<Responsibility> findByOrganizationIdIn(Set<UUID> organizationIds);

    void deleteByReferenceDataObjectIdAndOrganizationIdAndNation(UUID objectId, UUID organizationId, Nation nation);
}
