package energy.eddie.s3.models.referencedata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@SuppressWarnings("NullAway.Init")
@Table(name = "reference_data_responsibility", uniqueConstraints = @UniqueConstraint(columnNames = {
    "reference_data_object_id", "organization_id", "nation"
}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Responsibility {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reference_data_object_id", nullable = false)
    private ReferenceDataObject referenceDataObject;

    @Column(nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Nation nation;

    public Responsibility(ReferenceDataObject referenceDataObject, UUID organizationId, Nation nation) {
        this.referenceDataObject = referenceDataObject;
        this.organizationId = organizationId;
        this.nation = nation;
    }
}
