package energy.eddie.s3.repositories;

import energy.eddie.s3.models.referencedata.ReferenceDataEntryValue;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceDataEntryValueRepository extends JpaRepository<ReferenceDataEntryValue, UUID> {
    boolean existsByFieldId(UUID fieldId);
}
