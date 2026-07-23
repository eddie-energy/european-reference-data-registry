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
public class Field {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    protected UUID id;

    @Setter
    @Column(nullable = false)
    protected String name;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    protected DataType dataType;

    @Setter
    @Column(nullable = false)
    protected boolean mandatory;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column
    @Nullable
    protected Nation nation;

    @Column(nullable = false, updatable = false)
    protected Instant createdAt;

    @OneToMany(mappedBy = "field", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    protected List<EnumOption> options = new ArrayList<>();

    public Field(String name, DataType dataType, boolean mandatory, @Nullable Nation nation) {
        this.name = name;
        this.dataType = dataType;
        this.mandatory = mandatory;
        this.nation = nation;
        this.createdAt = Instant.now();
    }

    public void addOption(String optionName) {
        options.add(new EnumOption(this, optionName));
    }
}
