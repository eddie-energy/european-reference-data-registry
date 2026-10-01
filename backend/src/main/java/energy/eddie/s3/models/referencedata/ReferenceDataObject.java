package energy.eddie.s3.models.referencedata;

import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
public class ReferenceDataObject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    protected UUID id;

    @Setter
    @Column(nullable = false)
    protected String name;

    @Setter
    @Column(nullable = false)
    protected String description;

    @Setter
    @Nullable
    @Enumerated(EnumType.STRING)
    @Column
    protected ReferenceDataObjectCategory category;

    @Column(nullable = false, updatable = false)
    protected Instant createdAt;

    @OneToMany(mappedBy = "referenceDataObject", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    protected List<ReferenceDataObjectVersion> versions = new ArrayList<>();

    public ReferenceDataObject(String name, String description) {
        this.name = name;
        this.description = description;
        this.createdAt = Instant.now();
    }
}
