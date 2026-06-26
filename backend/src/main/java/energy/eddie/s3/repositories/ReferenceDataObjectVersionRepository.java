package energy.eddie.s3.repositories;

import energy.eddie.s3.models.referencedata.ReferenceDataObjectVersion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceDataObjectVersionRepository extends JpaRepository<ReferenceDataObjectVersion, UUID> {
    List<ReferenceDataObjectVersion> findByReferenceDataObjectId(UUID referenceDataObjectId);
}
