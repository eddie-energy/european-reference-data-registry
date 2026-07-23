package energy.eddie.s3.models.referencedata;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@SuppressWarnings("NullAway.Init")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class EntryValue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    protected UUID id;

    @Setter
    @Column
    @Nullable
    protected String textValue;

    @Setter
    @Column
    @Nullable
    protected BigDecimal numberValue;

    @Setter
    @Column
    @Nullable
    protected LocalDate dateValue;

    @Column(nullable = false, updatable = false)
    protected Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", referencedColumnName = "id", nullable = false, updatable = false)
    protected Entry entry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", referencedColumnName = "id", nullable = false, updatable = false)
    protected Field field;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enum_option_id", referencedColumnName = "id")
    @Nullable
    protected EnumOption enumOption;

    public EntryValue(Entry entry, Field field) {
        this.entry = entry;
        this.field = field;
        this.createdAt = Instant.now();
    }

    public void clear() {
        this.textValue = null;
        this.numberValue = null;
        this.dateValue = null;
        this.enumOption = null;
    }
}
