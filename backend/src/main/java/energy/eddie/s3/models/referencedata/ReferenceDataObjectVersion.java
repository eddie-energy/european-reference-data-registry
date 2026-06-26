package energy.eddie.s3.models.referencedata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@SuppressWarnings("NullAway.Init")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ReferenceDataObjectVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    protected UUID id;

    @Setter
    @Column(nullable = false)
    protected int majorVersion;

    @Setter
    @Column(nullable = false)
    protected int minorVersion;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    protected PublishState publishState;

    @Column(nullable = false, updatable = false)
    protected Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_data_object_id", referencedColumnName = "id", nullable = false, updatable = false)
    protected ReferenceDataObject referenceDataObject;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "reference_data_object_version_field",
            joinColumns = @JoinColumn(name = "version_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "field_id", referencedColumnName = "id"))
    protected List<Field> fields = new ArrayList<>();

    public ReferenceDataObjectVersion(ReferenceDataObject referenceDataObject, int majorVersion, int minorVersion,
            PublishState publishState) {
        this.referenceDataObject = referenceDataObject;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.publishState = publishState;
        this.createdAt = Instant.now();
    }
}
