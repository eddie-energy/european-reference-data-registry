package energy.eddie.s3.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import energy.eddie.s3.exceptions.ConflictException;
import energy.eddie.s3.exceptions.ForbiddenException;
import energy.eddie.s3.exceptions.NotFoundException;
import energy.eddie.s3.generated.model.ReferenceDataEntryValueDto;
import energy.eddie.s3.generated.model.Nation;
import energy.eddie.s3.generated.model.UpsertReferenceDataEntryRequest;
import energy.eddie.s3.models.referencedata.DataType;
import energy.eddie.s3.models.referencedata.ReferenceDataEntry;
import energy.eddie.s3.models.referencedata.Field;
import energy.eddie.s3.models.referencedata.PublishState;
import energy.eddie.s3.models.referencedata.ReferenceDataObject;
import energy.eddie.s3.models.referencedata.ReferenceDataObjectVersion;
import energy.eddie.s3.repositories.ReferenceDataEntryRepository;
import energy.eddie.s3.repositories.ReferenceDataObjectRepository;
import energy.eddie.s3.repositories.ReferenceDataObjectVersionRepository;
import energy.eddie.s3.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReferenceDataEntryServiceTest {

    @Mock
    private ReferenceDataObjectRepository referenceDataObjectRepository;
    @Mock
    private ReferenceDataObjectVersionRepository versionRepository;
    @Mock
    private ReferenceDataEntryRepository referenceDataEntryRepository;
    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private ReferenceDataEntryService service;

    @BeforeEach
    void grantOperationalEntity() {
        lenient().when(currentUser.maySeeDrafts()).thenReturn(true);
        lenient().when(currentUser.mayMaintainReferenceDataEntriesFor(any())).thenReturn(true);
    }

    private static final UUID OBJECT_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();

    private static ReferenceDataObject rdo() {
        var rdo = new ReferenceDataObject("Tariffs", "desc");
        ReflectionTestUtils.setField(rdo, "id", OBJECT_ID);
        return rdo;
    }

    private static ReferenceDataObjectVersion version(ReferenceDataObject rdo, Field... fields) {
        var version = new ReferenceDataObjectVersion(rdo, 1, PublishState.DRAFT);
        ReflectionTestUtils.setField(version, "id", VERSION_ID);
        version.getFields().addAll(List.of(fields));
        return version;
    }

    private static ReferenceDataObjectVersion version(ReferenceDataObject rdo, int versionCode, Field... fields) {
        var version = new ReferenceDataObjectVersion(rdo, versionCode, PublishState.DRAFT);
        ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
        version.getFields().addAll(List.of(fields));
        return version;
    }

    private void mockAllVersionsDesc(ReferenceDataObjectVersion... versionsDesc) {
        when(versionRepository.findByReferenceDataObjectIdOrderByVersionCodeDesc(OBJECT_ID))
                .thenReturn(List.of(versionsDesc));
    }

    private static Field field(String name, DataType dataType, boolean mandatory) {
        return field(name, dataType, mandatory, null);
    }

    private static Field field(
            String name, DataType dataType, boolean mandatory, energy.eddie.s3.models.referencedata.Nation nation) {
        var field = new Field(name, dataType, mandatory, nation);
        ReflectionTestUtils.setField(field, "id", UUID.randomUUID());
        return field;
    }

    private static UpsertReferenceDataEntryRequest request(List<ReferenceDataEntryValueDto> values) {
        return new UpsertReferenceDataEntryRequest(values).nation(Nation.AUT);
    }

    private static Field enumField(String name, String... options) {
        var field = field(name, DataType.ENUM, false);
        for (var option : options) {
            field.addOption(option);
            ReflectionTestUtils.setField(field.getOptions().getLast(), "id", UUID.randomUUID());
        }
        return field;
    }

    private static ReferenceDataEntry referenceDataEntry(ReferenceDataObject rdo) {
        var referenceDataEntry = new ReferenceDataEntry(rdo, energy.eddie.s3.models.referencedata.Nation.AUT);
        ReflectionTestUtils.setField(referenceDataEntry, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(referenceDataEntry, "createdAt", Instant.now());
        return referenceDataEntry;
    }

    private void mockVersion(ReferenceDataObjectVersion version) {
        when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
    }

    private void mockSave() {
        when(referenceDataEntryRepository.save(any(ReferenceDataEntry.class))).thenAnswer(invocation -> {
            ReferenceDataEntry saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            }
            return saved;
        });
    }

    @Test
    void createReferenceDataEntry_asNdsfOfAnotherNation_throwsForbidden() {
        when(currentUser.mayMaintainReferenceDataEntriesFor(energy.eddie.s3.models.referencedata.Nation.GER))
                .thenReturn(false);
        var rdo = rdo();
        mockVersion(publishedVersion(rdo));

        assertThatThrownBy(() -> service.createReferenceDataEntry(
                        OBJECT_ID, VERSION_ID, new UpsertReferenceDataEntryRequest(List.of()).nation(Nation.GER)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createReferenceDataEntry_asNdsfOfThatNation_isAllowed() {
        when(currentUser.mayMaintainReferenceDataEntriesFor(energy.eddie.s3.models.referencedata.Nation.AUT))
                .thenReturn(true);
        var rdo = rdo();
        mockVersion(publishedVersion(rdo));
        mockSave();

        assertThat(service.createReferenceDataEntry(OBJECT_ID, VERSION_ID, request(List.of())).getNation())
                .isEqualTo(Nation.AUT);
    }

    @Test
    void listReferenceDataEntries_ofADraftVersion_isNotFoundForRolesThatDoNotSeeDrafts() {
        when(currentUser.maySeeDrafts()).thenReturn(false);
        mockVersion(version(rdo()));

        assertThatThrownBy(() -> service.listReferenceDataEntries(OBJECT_ID, VERSION_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listReferenceDataEntries_ofADraftVersion_isAllowedForRolesThatSeeDrafts() {
        var rdo = rdo();
        var draft = version(rdo);
        mockVersion(draft);
        mockAllVersionsDesc(draft);
        when(referenceDataEntryRepository.findByReferenceDataObjectIdOrderByCreatedAtAsc(OBJECT_ID))
                .thenReturn(List.of());

        assertThat(service.listReferenceDataEntries(OBJECT_ID, VERSION_ID)).isEmpty();
    }

    private static ReferenceDataObjectVersion publishedVersion(ReferenceDataObject rdo, Field... fields) {
        var version = version(rdo, fields);
        version.setPublishState(PublishState.PUBLISHED);
        return version;
    }

    @Test
    void createReferenceDataEntry_storesTypedValues() {
        var rdo = rdo();
        var text = field("name", DataType.TEXT, true);
        var number = field("price", DataType.NUMBER, false);
        mockVersion(version(rdo, text, number));
        mockSave();

        var result = service.createReferenceDataEntry(OBJECT_ID, VERSION_ID, request(List.of(
                new ReferenceDataEntryValueDto(text.getId()).textValue("Vienna"),
                new ReferenceDataEntryValueDto(number.getId()).numberValue(new BigDecimal("42.5")))));

        assertThat(result.getComplete()).isTrue();
        assertThat(result.getValues()).hasSize(2);
        assertThat(result.getValues().getFirst().getTextValue()).isEqualTo("Vienna");
        assertThat(result.getValues().getLast().getNumberValue()).isEqualByComparingTo("42.5");
    }

    @Test
    void createReferenceDataEntry_missingMandatoryValue_isIncomplete() {
        var rdo = rdo();
        var text = field("name", DataType.TEXT, true);
        var mandatoryNumber = field("price", DataType.NUMBER, true);
        mockVersion(version(rdo, text, mandatoryNumber));
        mockSave();

        var result = service.createReferenceDataEntry(OBJECT_ID, VERSION_ID, request(List.of(
                new ReferenceDataEntryValueDto(text.getId()).textValue("Vienna"))));

        assertThat(result.getComplete()).isFalse();
        assertThat(result.getValues()).hasSize(2);
        assertThat(result.getValues().getLast().getNumberValue()).isNull();
    }

    @Test
    void createReferenceDataEntry_typeMismatch_throwsConflict() {
        var rdo = rdo();
        var number = field("price", DataType.NUMBER, false);
        mockVersion(version(rdo, number));

        var request = request(List.of(new ReferenceDataEntryValueDto(number.getId()).textValue("nope")));

        assertThatThrownBy(() -> service.createReferenceDataEntry(OBJECT_ID, VERSION_ID, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createReferenceDataEntry_multipleValueSlots_throwsConflict() {
        var rdo = rdo();
        var text = field("name", DataType.TEXT, false);
        mockVersion(version(rdo, text));

        var request = request(List.of(
                new ReferenceDataEntryValueDto(text.getId()).textValue("Vienna").numberValue(BigDecimal.ONE)));

        assertThatThrownBy(() -> service.createReferenceDataEntry(OBJECT_ID, VERSION_ID, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createReferenceDataEntry_enumOptionOfAnotherField_throwsConflict() {
        var rdo = rdo();
        var role = enumField("role", "DSO");
        var foreign = enumField("other", "TSO");
        mockVersion(version(rdo, role));

        var request = request(List.of(
                new ReferenceDataEntryValueDto(role.getId()).enumOptionId(foreign.getOptions().getFirst().getId())));

        assertThatThrownBy(() -> service.createReferenceDataEntry(OBJECT_ID, VERSION_ID, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createReferenceDataEntry_enumOptionOfField_isStored() {
        var rdo = rdo();
        var role = enumField("role", "DSO", "TSO");
        mockVersion(version(rdo, role));
        mockSave();

        var optionId = role.getOptions().getFirst().getId();
        var result = service.createReferenceDataEntry(OBJECT_ID, VERSION_ID, request(List.of(
                new ReferenceDataEntryValueDto(role.getId()).enumOptionId(optionId))));

        assertThat(result.getValues().getFirst().getEnumOptionId()).isEqualTo(optionId);
    }

    @Test
    void createReferenceDataEntry_fieldNotInVersion_throwsNotFound() {
        var rdo = rdo();
        mockVersion(version(rdo, field("name", DataType.TEXT, false)));

        var request = request(List.of(new ReferenceDataEntryValueDto(UUID.randomUUID()).textValue("x")));

        assertThatThrownBy(() -> service.createReferenceDataEntry(OBJECT_ID, VERSION_ID, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateReferenceDataEntry_omittedField_clearsItsValue() {
        var rdo = rdo();
        var text = field("name", DataType.TEXT, false);
        var version = version(rdo, text);
        var referenceDataEntry = referenceDataEntry(rdo);
        referenceDataEntry.putValue(text).setTextValue("Vienna");
        mockVersion(version);
        when(referenceDataObjectRepository.existsById(OBJECT_ID)).thenReturn(true);
        when(referenceDataEntryRepository.findById(referenceDataEntry.getId())).thenReturn(Optional.of(referenceDataEntry));
        mockSave();

        var result = service.updateReferenceDataEntry(OBJECT_ID, VERSION_ID, referenceDataEntry.getId(), request(List.of()));

        assertThat(referenceDataEntry.getValues()).isEmpty();
        assertThat(result.getValues().getFirst().getTextValue()).isNull();
    }

    @Test
    void updateReferenceDataEntry_leavesValuesOfFieldsOutsideTheVersionUntouched() {
        var rdo = rdo();
        var inVersion = field("name", DataType.TEXT, false);
        var otherVersionField = field("legacy", DataType.TEXT, false);
        var referenceDataEntry = referenceDataEntry(rdo);
        referenceDataEntry.putValue(otherVersionField).setTextValue("kept");
        mockVersion(version(rdo, inVersion));
        when(referenceDataObjectRepository.existsById(OBJECT_ID)).thenReturn(true);
        when(referenceDataEntryRepository.findById(referenceDataEntry.getId())).thenReturn(Optional.of(referenceDataEntry));
        mockSave();

        service.updateReferenceDataEntry(OBJECT_ID, VERSION_ID, referenceDataEntry.getId(), request(List.of(
                new ReferenceDataEntryValueDto(inVersion.getId()).textValue("Vienna"))));

        assertThat(referenceDataEntry.findValue(otherVersionField.getId())).isPresent();
        assertThat(referenceDataEntry.findValue(otherVersionField.getId()).orElseThrow().getTextValue()).isEqualTo("kept");
    }

    @Test
    void deleteReferenceDataEntry_ofAnotherObject_throwsNotFound() {
        var otherRdo = new ReferenceDataObject("Other", "desc");
        ReflectionTestUtils.setField(otherRdo, "id", UUID.randomUUID());
        var referenceDataEntry = referenceDataEntry(otherRdo);
        when(referenceDataObjectRepository.existsById(OBJECT_ID)).thenReturn(true);
        when(referenceDataEntryRepository.findById(referenceDataEntry.getId())).thenReturn(Optional.of(referenceDataEntry));

        var referenceDataEntryId = referenceDataEntry.getId();

        assertThatThrownBy(() -> service.deleteReferenceDataEntry(OBJECT_ID, referenceDataEntryId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createReferenceDataEntry_mandatoryFieldOfAnotherNation_doesNotBlockCompleteness() {
        var rdo = rdo();
        var shared = field("name", DataType.TEXT, true);
        var frenchOnly = field("iban", DataType.TEXT, true, energy.eddie.s3.models.referencedata.Nation.FRA);
        mockVersion(version(rdo, shared, frenchOnly));
        mockSave();

        var result = service.createReferenceDataEntry(OBJECT_ID, VERSION_ID, request(List.of(
                new ReferenceDataEntryValueDto(shared.getId()).textValue("Vienna"))));

        assertThat(result.getNation()).isEqualTo(Nation.AUT);
        assertThat(result.getComplete()).isTrue();
    }

    @Test
    void listReferenceDataEntries_projectsStoredValuesOntoTheRequestedVersion() {
        var rdo = rdo();
        var v1Field = field("name", DataType.TEXT, true);
        var v2Field = field("country", DataType.TEXT, true);
        var referenceDataEntry = referenceDataEntry(rdo);
        referenceDataEntry.putValue(v1Field).setTextValue("Vienna");
        mockVersion(version(rdo, v1Field, v2Field));
        when(referenceDataEntryRepository.findByReferenceDataObjectIdOrderByCreatedAtAsc(OBJECT_ID)).thenReturn(List.of(referenceDataEntry));

        var result = service.listReferenceDataEntries(OBJECT_ID, VERSION_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getComplete()).isFalse();
        assertThat(result.getFirst().getValues()).extracting(ReferenceDataEntryValueDto::getTextValue)
                .containsExactly("Vienna", null);
    }

    @Test
    void listReferenceDataEntries_incomplete_reportsLastCompleteVersionCode() {
        var rdo = rdo();
        var name = field("name", DataType.TEXT, true);
        var country = field("country", DataType.TEXT, true);
        var v1 = version(rdo, 1, name);
        var v2 = version(rdo, 2, name, country);
        var referenceDataEntry = referenceDataEntry(rdo);
        referenceDataEntry.putValue(name).setTextValue("Vienna");
        mockVersion(v2);
        mockAllVersionsDesc(v2, v1);
        when(referenceDataEntryRepository.findByReferenceDataObjectIdOrderByCreatedAtAsc(OBJECT_ID)).thenReturn(List.of(referenceDataEntry));

        var result = service.listReferenceDataEntries(OBJECT_ID, VERSION_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getComplete()).isFalse();
        assertThat(result.getFirst().getLastCompleteVersionCode()).isEqualTo(1);
    }

    @Test
    void listReferenceDataEntries_neverComplete_lastCompleteVersionCodeIsNull() {
        var rdo = rdo();
        var mandatory = field("name", DataType.TEXT, true);
        var v1 = version(rdo, 1, mandatory);
        var referenceDataEntry = referenceDataEntry(rdo);
        mockVersion(v1);
        mockAllVersionsDesc(v1);
        when(referenceDataEntryRepository.findByReferenceDataObjectIdOrderByCreatedAtAsc(OBJECT_ID)).thenReturn(List.of(referenceDataEntry));

        var result = service.listReferenceDataEntries(OBJECT_ID, VERSION_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getComplete()).isFalse();
        assertThat(result.getFirst().getLastCompleteVersionCode()).isNull();
    }

    @Test
    void createReferenceDataEntry_complete_lastCompleteVersionCodeMatchesCurrentVersion() {
        var rdo = rdo();
        var text = field("name", DataType.TEXT, true);
        var v1 = version(rdo, 1, text);
        mockVersion(v1);
        mockAllVersionsDesc(v1);
        mockSave();

        var result = service.createReferenceDataEntry(OBJECT_ID, VERSION_ID, request(List.of(
                new ReferenceDataEntryValueDto(text.getId()).textValue("Vienna"))));

        assertThat(result.getComplete()).isTrue();
        assertThat(result.getLastCompleteVersionCode()).isEqualTo(1);
    }
}
