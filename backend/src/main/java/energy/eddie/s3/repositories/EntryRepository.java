package energy.eddie.s3.repositories;

import energy.eddie.s3.models.referencedata.Entry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntryRepository extends JpaRepository<Entry, UUID> {
    List<Entry> findByReferenceDataObjectIdOrderByCreatedAtAsc(UUID referenceDataObjectId);

    boolean existsByReferenceDataObjectId(UUID referenceDataObjectId);
}
