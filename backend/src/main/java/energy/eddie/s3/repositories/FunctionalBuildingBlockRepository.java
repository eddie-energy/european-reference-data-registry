package energy.eddie.s3.repositories;

import energy.eddie.s3.models.referencedata.FunctionalBuildingBlock;
import energy.eddie.s3.models.referencedata.FunctionalBuildingBlockLink;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FunctionalBuildingBlockRepository
        extends JpaRepository<FunctionalBuildingBlockLink, FunctionalBuildingBlock> {
    @Query("select b.code as code, b.url as url from FunctionalBuildingBlockLink b where b.code in :codes")
    List<Link> findLinksByCodeIn(Collection<FunctionalBuildingBlock> codes);

    interface Link {
        FunctionalBuildingBlock getCode();

        @Nullable
        String getUrl();
    }
}
