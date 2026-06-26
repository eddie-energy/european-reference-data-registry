package energy.eddie.s3.repositories;

import energy.eddie.s3.models.referencedata.Field;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FieldRepository extends JpaRepository<Field, UUID> {
}
