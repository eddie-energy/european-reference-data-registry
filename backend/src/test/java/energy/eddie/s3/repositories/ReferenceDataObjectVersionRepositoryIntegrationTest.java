package energy.eddie.s3.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import energy.eddie.s3.models.referencedata.DataType;
import energy.eddie.s3.models.referencedata.EnumOption;
import energy.eddie.s3.models.referencedata.Field;
import energy.eddie.s3.models.referencedata.PublishState;
import energy.eddie.s3.models.referencedata.ReferenceDataObject;
import energy.eddie.s3.models.referencedata.ReferenceDataObjectVersion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReferenceDataObjectVersionRepositoryIntegrationTest {

    @Autowired
    private ReferenceDataObjectRepository referenceDataObjectRepository;
    @Autowired
    private ReferenceDataObjectVersionRepository versionRepository;
    @Autowired
    private FieldRepository fieldRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void findFirstByReferenceDataObjectIdOrderByVersionCodeDesc_returnsLatest() {
        var rdo = new ReferenceDataObject("Tariffs", "desc");
        rdo.getVersions().add(new ReferenceDataObjectVersion(rdo, 1, PublishState.PUBLISHED));
        rdo.getVersions().add(new ReferenceDataObjectVersion(rdo, 2, PublishState.DRAFT));
        var saved = referenceDataObjectRepository.save(rdo);

        var latest = versionRepository
                .findFirstByReferenceDataObjectIdOrderByVersionCodeDesc(saved.getId());

        assertThat(latest).isPresent();
        assertThat(latest.get().getVersionCode()).isEqualTo(2);
    }

    @Test
    void findByReferenceDataObjectIdOrderByVersionCodeDesc_returnsDescending() {
        var rdo = new ReferenceDataObject("Tariffs", "desc");
        rdo.getVersions().add(new ReferenceDataObjectVersion(rdo, 1, PublishState.PUBLISHED));
        rdo.getVersions().add(new ReferenceDataObjectVersion(rdo, 2, PublishState.PUBLISHED));
        rdo.getVersions().add(new ReferenceDataObjectVersion(rdo, 3, PublishState.DRAFT));
        var saved = referenceDataObjectRepository.save(rdo);

        var versions = versionRepository
                .findByReferenceDataObjectIdOrderByVersionCodeDesc(saved.getId());

        assertThat(versions).extracting(ReferenceDataObjectVersion::getVersionCode)
                .containsExactly(3, 2, 1);
    }

    @Test
    void countByFieldsId_countsLinkingVersions() {
        var rdo = new ReferenceDataObject("Tariffs", "desc");
        var v1 = new ReferenceDataObjectVersion(rdo, 1, PublishState.PUBLISHED);
        var v2 = new ReferenceDataObjectVersion(rdo, 2, PublishState.DRAFT);
        rdo.getVersions().add(v1);
        rdo.getVersions().add(v2);

        var field = fieldRepository.save(new Field("price", DataType.NUMBER, true, null));
        v1.getFields().add(field);
        v2.getFields().add(field);
        referenceDataObjectRepository.save(rdo);

        assertThat(versionRepository.countByFieldsId(field.getId())).isEqualTo(2);
    }

    @Test
    void enumField_persistsOptions() {
        var field = new Field("role", DataType.ENUM, true, null);
        field.addOption("DATA_HUB");
        field.addOption("DSO");
        var saved = fieldRepository.saveAndFlush(field);
        entityManager.clear();

        var reloaded = fieldRepository.findById(saved.getId());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getOptions()).extracting(EnumOption::getName)
                .containsExactlyInAnyOrder("DATA_HUB", "DSO");
    }

    @Test
    void fields_persistOrderAcrossReload() {
        var rdo = new ReferenceDataObject("Tariffs", "desc");
        var version = new ReferenceDataObjectVersion(rdo, 1, PublishState.DRAFT);
        rdo.getVersions().add(version);
        var fieldA = fieldRepository.save(new Field("a", DataType.NUMBER, false, null));
        var fieldB = fieldRepository.save(new Field("b", DataType.NUMBER, false, null));
        var fieldC = fieldRepository.save(new Field("c", DataType.NUMBER, false, null));
        version.getFields().addAll(List.of(fieldA, fieldB, fieldC));
        var savedRdo = referenceDataObjectRepository.saveAndFlush(rdo);
        var versionId = savedRdo.getVersions().get(0).getId();
        entityManager.clear();

        var reloaded = versionRepository.findById(versionId).orElseThrow();
        assertThat(reloaded.getFields()).extracting(Field::getId)
                .containsExactly(fieldA.getId(), fieldB.getId(), fieldC.getId());

        var byId = reloaded.getFields().stream()
                .collect(Collectors.toMap(Field::getId, field -> field));
        var reordered = List.of(fieldC.getId(), fieldA.getId(), fieldB.getId()).stream()
                .map(byId::get)
                .toList();
        reloaded.getFields().clear();
        reloaded.getFields().addAll(reordered);
        versionRepository.saveAndFlush(reloaded);
        entityManager.clear();

        var reReloaded = versionRepository.findById(versionId).orElseThrow();
        assertThat(reReloaded.getFields()).extracting(Field::getId)
                .containsExactly(fieldC.getId(), fieldA.getId(), fieldB.getId());
    }

    @Test
    void deletingField_removesItsOptions() {
        var field = new Field("role", DataType.ENUM, true, null);
        field.addOption("DSO");
        var saved = fieldRepository.saveAndFlush(field);

        fieldRepository.delete(saved);
        fieldRepository.flush();
        entityManager.clear();

        var remaining = entityManager
                .createQuery("select count(o) from EnumOption o where o.field.id = :fieldId", Long.class)
                .setParameter("fieldId", saved.getId())
                .getSingleResult();
        assertThat(remaining).isZero();
    }
}
