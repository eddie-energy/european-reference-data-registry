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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@SuppressWarnings("NullAway.Init")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Entry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    protected UUID id;

    @Column(nullable = false, updatable = false)
    protected Instant createdAt;

    @Column(nullable = false)
    protected Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_data_object_id", referencedColumnName = "id", nullable = false, updatable = false)
    protected ReferenceDataObject referenceDataObject;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "nation")
    @Nullable
    protected Nation nation;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    protected List<EntryValue> values = new ArrayList<>();

    public Entry(ReferenceDataObject referenceDataObject, @Nullable Nation nation) {
        this.referenceDataObject = referenceDataObject;
        this.nation = nation;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Optional<EntryValue> findValue(UUID fieldId) {
        return values.stream()
                .filter(value -> value.getField().getId().equals(fieldId))
                .findFirst();
    }

    public EntryValue putValue(Field field) {
        var existing = findValue(field.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        var value = new EntryValue(this, field);
        values.add(value);
        return value;
    }

    public void removeValue(UUID fieldId) {
        values.removeIf(value -> value.getField().getId().equals(fieldId));
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
