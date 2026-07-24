package energy.eddie.s3.repositories;

import energy.eddie.s3.models.referencedata.EntryValue;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntryValueRepository extends JpaRepository<EntryValue, UUID> {
    boolean existsByFieldId(UUID fieldId);
}
