package energy.eddie.s3.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import energy.eddie.s3.generated.model.EntryValueDto;
import energy.eddie.s3.generated.model.Nation;
import energy.eddie.s3.generated.model.UpsertEntryRequest;
import energy.eddie.s3.models.referencedata.DataType;
import energy.eddie.s3.models.referencedata.Field;
import energy.eddie.s3.models.referencedata.PublishState;
import energy.eddie.s3.models.referencedata.ReferenceDataObject;
import energy.eddie.s3.models.referencedata.ReferenceDataObjectVersion;
import energy.eddie.s3.services.EntryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EntryRepositoryIntegrationTest {

    @Autowired
    private ReferenceDataObjectRepository referenceDataObjectRepository;
    @Autowired
    private ReferenceDataObjectVersionRepository versionRepository;
    @Autowired
    private FieldRepository fieldRepository;
    @Autowired
    private EntryValueRepository entryValueRepository;
    @Autowired
    private EntryService entryService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void entryCreatedInV1_survivesIntoV2AndIsFlaggedIncomplete() {
        var rdo = new ReferenceDataObject("Tariffs", "desc");
        var v1 = new ReferenceDataObjectVersion(rdo, 1, PublishState.DRAFT);
        rdo.getVersions().add(v1);
        var name = fieldRepository.save(new Field("name", DataType.TEXT, true, null));
        v1.getFields().add(name);
        var savedRdo = referenceDataObjectRepository.save(rdo);

        var created = entryService.createEntry(savedRdo.getId(), v1.getId(), new UpsertEntryRequest(Nation.AUT, List.of(
                new EntryValueDto(name.getId()).textValue("Vienna"))));
        assertThat(created.getComplete()).isTrue();

        var v2 = new ReferenceDataObjectVersion(savedRdo, 2, PublishState.DRAFT);
        v2.getFields().add(name);
        var country = fieldRepository.save(new Field("country", DataType.TEXT, true, null));
        v2.getFields().add(country);
        versionRepository.save(v2);
        entityManager.flush();
        entityManager.clear();

        var v1Entries = entryService.listEntries(savedRdo.getId(), v1.getId());
        var v2Entries = entryService.listEntries(savedRdo.getId(), v2.getId());

        assertThat(v1Entries).hasSize(1);
        assertThat(v1Entries.getFirst().getComplete()).isTrue();
        assertThat(v1Entries.getFirst().getValues()).extracting(EntryValueDto::getTextValue)
                .containsExactly("Vienna");

        assertThat(v2Entries).hasSize(1);
        assertThat(v2Entries.getFirst().getId()).isEqualTo(created.getId());
        assertThat(v2Entries.getFirst().getComplete()).isFalse();
        assertThat(v2Entries.getFirst().getValues()).extracting(EntryValueDto::getTextValue)
                .containsExactly("Vienna", null);
    }

    @Test
    void enumValue_persistsReferenceToOption() {
        var rdo = new ReferenceDataObject("Roles", "desc");
        var version = new ReferenceDataObjectVersion(rdo, 1, PublishState.DRAFT);
        rdo.getVersions().add(version);
        var role = new Field("role", DataType.ENUM, false, null);
        role.addOption("DSO");
        role.addOption("TSO");
        var savedField = fieldRepository.save(role);
        version.getFields().add(savedField);
        var savedRdo = referenceDataObjectRepository.save(rdo);
        var optionId = savedField.getOptions().getFirst().getId();

        var created = entryService.createEntry(savedRdo.getId(), version.getId(), new UpsertEntryRequest(Nation.AUT, List.of(
                new EntryValueDto(savedField.getId()).enumOptionId(optionId))));
        entityManager.flush();
        entityManager.clear();

        var reloaded = entryService.listEntries(savedRdo.getId(), version.getId());

        assertThat(reloaded).hasSize(1);
        assertThat(reloaded.getFirst().getId()).isEqualTo(created.getId());
        assertThat(reloaded.getFirst().getValues().getFirst().getEnumOptionId()).isEqualTo(optionId);
    }

    @Test
    void existsByFieldId_reportsFieldsUsedByEntries() {
        var rdo = new ReferenceDataObject("Tariffs", "desc");
        var version = new ReferenceDataObjectVersion(rdo, 1, PublishState.DRAFT);
        rdo.getVersions().add(version);
        var used = fieldRepository.save(new Field("name", DataType.TEXT, false, null));
        var unused = fieldRepository.save(new Field("note", DataType.TEXT, false, null));
        version.getFields().add(used);
        version.getFields().add(unused);
        var savedRdo = referenceDataObjectRepository.save(rdo);

        entryService.createEntry(savedRdo.getId(), version.getId(), new UpsertEntryRequest(Nation.AUT, List.of(
                new EntryValueDto(used.getId()).textValue("Vienna"))));
        entityManager.flush();

        assertThat(entryValueRepository.existsByFieldId(used.getId())).isTrue();
        assertThat(entryValueRepository.existsByFieldId(unused.getId())).isFalse();
    }
}
