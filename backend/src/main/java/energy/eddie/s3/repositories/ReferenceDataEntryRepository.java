package energy.eddie.s3.repositories;

import energy.eddie.s3.models.referencedata.ReferenceDataEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceDataEntryRepository extends JpaRepository<ReferenceDataEntry, UUID> {
    List<ReferenceDataEntry> findByReferenceDataObjectIdOrderByCreatedAtAsc(UUID referenceDataObjectId);

    boolean existsByReferenceDataObjectId(UUID referenceDataObjectId);
}
