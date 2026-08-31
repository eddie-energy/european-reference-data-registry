package energy.eddie.s3.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import energy.eddie.s3.generated.model.ReferenceDataEntryValueDto;
import energy.eddie.s3.generated.model.Nation;
import energy.eddie.s3.generated.model.UpsertReferenceDataEntryRequest;
import energy.eddie.s3.models.referencedata.DataType;
import energy.eddie.s3.models.referencedata.Field;
import energy.eddie.s3.models.referencedata.PublishState;
import energy.eddie.s3.models.referencedata.ReferenceDataObject;
import energy.eddie.s3.models.referencedata.ReferenceDataObjectVersion;
import energy.eddie.s3.services.ReferenceDataEntryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import energy.eddie.s3.security.CeedsRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReferenceDataEntryRepositoryIntegrationTest {

    @Autowired
    private ReferenceDataObjectRepository referenceDataObjectRepository;
    @Autowired
    private ReferenceDataObjectVersionRepository versionRepository;
    @Autowired
    private FieldRepository fieldRepository;
    @Autowired
    private ReferenceDataEntryValueRepository referenceDataEntryValueRepository;
    @Autowired
    private ReferenceDataEntryService referenceDataEntryService;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void authenticateAsOperationalEntity() {
        var jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .claim("preferred_username", "operational-entity")
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority())),
                        "operational-entity"));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void referenceDataEntryCreatedInV1_survivesIntoV2AndIsFlaggedIncomplete() {
        var rdo = new ReferenceDataObject("Tariffs", "desc");
        var v1 = new ReferenceDataObjectVersion(rdo, 1, PublishState.DRAFT);
        rdo.getVersions().add(v1);
        var name = fieldRepository.save(new Field("name", DataType.TEXT, true, null));
        v1.getFields().add(name);
        var savedRdo = referenceDataObjectRepository.save(rdo);

        var created = referenceDataEntryService.createReferenceDataEntry(savedRdo.getId(), v1.getId(), new UpsertReferenceDataEntryRequest(List.of(
                new ReferenceDataEntryValueDto(name.getId()).textValue("Vienna"))).nation(Nation.AUT));
        assertThat(created.getComplete()).isTrue();

        var v2 = new ReferenceDataObjectVersion(savedRdo, 2, PublishState.DRAFT);
        v2.getFields().add(name);
        var country = fieldRepository.save(new Field("country", DataType.TEXT, true, null));
        v2.getFields().add(country);
        versionRepository.save(v2);
        entityManager.flush();
        entityManager.clear();

        var v1ReferenceDataEntries =
                referenceDataEntryService.listReferenceDataEntries(savedRdo.getId(), v1.getId());
        var v2ReferenceDataEntries =
                referenceDataEntryService.listReferenceDataEntries(savedRdo.getId(), v2.getId());

        assertThat(v1ReferenceDataEntries).hasSize(1);
        assertThat(v1ReferenceDataEntries.getFirst().getComplete()).isTrue();
        assertThat(v1ReferenceDataEntries.getFirst().getLastCompleteVersionCode()).isEqualTo(1);
        assertThat(v1ReferenceDataEntries.getFirst().getValues())
                .extracting(ReferenceDataEntryValueDto::getTextValue)
                .containsExactly("Vienna");

        assertThat(v2ReferenceDataEntries).hasSize(1);
        assertThat(v2ReferenceDataEntries.getFirst().getId()).isEqualTo(created.getId());
        assertThat(v2ReferenceDataEntries.getFirst().getComplete()).isFalse();
        assertThat(v2ReferenceDataEntries.getFirst().getLastCompleteVersionCode()).isEqualTo(1);
        assertThat(v2ReferenceDataEntries.getFirst().getValues())
                .extracting(ReferenceDataEntryValueDto::getTextValue)
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

        var created = referenceDataEntryService.createReferenceDataEntry(savedRdo.getId(), version.getId(), new UpsertReferenceDataEntryRequest(List.of(
                new ReferenceDataEntryValueDto(savedField.getId()).enumOptionId(optionId))).nation(Nation.AUT));
        entityManager.flush();
        entityManager.clear();

        var reloaded = referenceDataEntryService.listReferenceDataEntries(savedRdo.getId(), version.getId());

        assertThat(reloaded).hasSize(1);
        assertThat(reloaded.getFirst().getId()).isEqualTo(created.getId());
        assertThat(reloaded.getFirst().getValues().getFirst().getEnumOptionId()).isEqualTo(optionId);
    }

    @Test
    void existsByFieldId_reportsFieldsUsedByReferenceDataEntries() {
        var rdo = new ReferenceDataObject("Tariffs", "desc");
        var version = new ReferenceDataObjectVersion(rdo, 1, PublishState.DRAFT);
        rdo.getVersions().add(version);
        var used = fieldRepository.save(new Field("name", DataType.TEXT, false, null));
        var unused = fieldRepository.save(new Field("note", DataType.TEXT, false, null));
        version.getFields().add(used);
        version.getFields().add(unused);
        var savedRdo = referenceDataObjectRepository.save(rdo);

        referenceDataEntryService.createReferenceDataEntry(savedRdo.getId(), version.getId(), new UpsertReferenceDataEntryRequest(List.of(
                new ReferenceDataEntryValueDto(used.getId()).textValue("Vienna"))).nation(Nation.AUT));
        entityManager.flush();

        assertThat(referenceDataEntryValueRepository.existsByFieldId(used.getId())).isTrue();
        assertThat(referenceDataEntryValueRepository.existsByFieldId(unused.getId())).isFalse();
    }
}
