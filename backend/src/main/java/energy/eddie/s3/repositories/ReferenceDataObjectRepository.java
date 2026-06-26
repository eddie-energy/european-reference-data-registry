package energy.eddie.s3.repositories;

import energy.eddie.s3.models.referencedata.ReferenceDataObject;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceDataObjectRepository extends JpaRepository<ReferenceDataObject, UUID> {
}
