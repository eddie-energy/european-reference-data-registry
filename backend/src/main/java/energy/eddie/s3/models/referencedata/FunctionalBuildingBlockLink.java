package energy.eddie.s3.models.referencedata;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "functional_building_block")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@SuppressWarnings("NullAway.Init")
public class FunctionalBuildingBlockLink {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private FunctionalBuildingBlock code;

    @Nullable
    @Column
    private String url;
}
