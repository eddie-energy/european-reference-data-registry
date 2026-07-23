package energy.eddie.s3.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import energy.eddie.s3.exceptions.ConflictException;
import energy.eddie.s3.exceptions.NotFoundException;
import energy.eddie.s3.generated.model.EntryValueDto;
import energy.eddie.s3.generated.model.Nation;
import energy.eddie.s3.generated.model.UpsertEntryRequest;
import energy.eddie.s3.models.referencedata.DataType;
import energy.eddie.s3.models.referencedata.Entry;
import energy.eddie.s3.models.referencedata.Field;
import energy.eddie.s3.models.referencedata.PublishState;
import energy.eddie.s3.models.referencedata.ReferenceDataObject;
import energy.eddie.s3.models.referencedata.ReferenceDataObjectVersion;
import energy.eddie.s3.repositories.EntryRepository;
import energy.eddie.s3.repositories.ReferenceDataObjectRepository;
import energy.eddie.s3.repositories.ReferenceDataObjectVersionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EntryServiceTest {

    @Mock
    private ReferenceDataObjectRepository referenceDataObjectRepository;
    @Mock
    private ReferenceDataObjectVersionRepository versionRepository;
    @Mock
    private EntryRepository entryRepository;

    @InjectMocks
    private EntryService service;

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

    private static Field field(String name, DataType dataType, boolean mandatory) {
        return field(name, dataType, mandatory, null);
    }

    private static Field field(
            String name, DataType dataType, boolean mandatory, energy.eddie.s3.models.referencedata.Nation nation) {
        var field = new Field(name, dataType, mandatory, nation);
        ReflectionTestUtils.setField(field, "id", UUID.randomUUID());
        return field;
    }

    private static UpsertEntryRequest request(List<EntryValueDto> values) {
        return new UpsertEntryRequest(Nation.AUT, values);
    }

    private static Field enumField(String name, String... options) {
        var field = field(name, DataType.ENUM, false);
        for (var option : options) {
            field.addOption(option);
            ReflectionTestUtils.setField(field.getOptions().getLast(), "id", UUID.randomUUID());
        }
        return field;
    }

    private static Entry entry(ReferenceDataObject rdo) {
        var entry = new Entry(rdo, energy.eddie.s3.models.referencedata.Nation.AUT);
        ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(entry, "createdAt", Instant.now());
        return entry;
    }

    private void mockVersion(ReferenceDataObjectVersion version) {
        when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
    }

    private void mockSave() {
        when(entryRepository.save(any(Entry.class))).thenAnswer(invocation -> {
            Entry saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            }
            return saved;
        });
    }

    @Test
    void createEntry_storesTypedValues() {
        var rdo = rdo();
        var text = field("name", DataType.TEXT, true);
        var number = field("price", DataType.NUMBER, false);
        mockVersion(version(rdo, text, number));
        mockSave();

        var result = service.createEntry(OBJECT_ID, VERSION_ID, request(List.of(
                new EntryValueDto(text.getId()).textValue("Vienna"),
                new EntryValueDto(number.getId()).numberValue(new BigDecimal("42.5")))));

        assertThat(result.getComplete()).isTrue();
        assertThat(result.getValues()).hasSize(2);
        assertThat(result.getValues().getFirst().getTextValue()).isEqualTo("Vienna");
        assertThat(result.getValues().getLast().getNumberValue()).isEqualByComparingTo("42.5");
    }

    @Test
    void createEntry_missingMandatoryValue_isIncomplete() {
        var rdo = rdo();
        var text = field("name", DataType.TEXT, true);
        var mandatoryNumber = field("price", DataType.NUMBER, true);
        mockVersion(version(rdo, text, mandatoryNumber));
        mockSave();

        var result = service.createEntry(OBJECT_ID, VERSION_ID, request(List.of(
                new EntryValueDto(text.getId()).textValue("Vienna"))));

        assertThat(result.getComplete()).isFalse();
        assertThat(result.getValues()).hasSize(2);
        assertThat(result.getValues().getLast().getNumberValue()).isNull();
    }

    @Test
    void createEntry_typeMismatch_throwsConflict() {
        var rdo = rdo();
        var number = field("price", DataType.NUMBER, false);
        mockVersion(version(rdo, number));

        var request = request(List.of(new EntryValueDto(number.getId()).textValue("nope")));

        assertThatThrownBy(() -> service.createEntry(OBJECT_ID, VERSION_ID, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createEntry_multipleValueSlots_throwsConflict() {
        var rdo = rdo();
        var text = field("name", DataType.TEXT, false);
        mockVersion(version(rdo, text));

        var request = request(List.of(
                new EntryValueDto(text.getId()).textValue("Vienna").numberValue(BigDecimal.ONE)));

        assertThatThrownBy(() -> service.createEntry(OBJECT_ID, VERSION_ID, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createEntry_enumOptionOfAnotherField_throwsConflict() {
        var rdo = rdo();
        var role = enumField("role", "DSO");
        var foreign = enumField("other", "TSO");
        mockVersion(version(rdo, role));

        var request = request(List.of(
                new EntryValueDto(role.getId()).enumOptionId(foreign.getOptions().getFirst().getId())));

        assertThatThrownBy(() -> service.createEntry(OBJECT_ID, VERSION_ID, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createEntry_enumOptionOfField_isStored() {
        var rdo = rdo();
        var role = enumField("role", "DSO", "TSO");
        mockVersion(version(rdo, role));
        mockSave();

        var optionId = role.getOptions().getFirst().getId();
        var result = service.createEntry(OBJECT_ID, VERSION_ID, request(List.of(
                new EntryValueDto(role.getId()).enumOptionId(optionId))));

        assertThat(result.getValues().getFirst().getEnumOptionId()).isEqualTo(optionId);
    }

    @Test
    void createEntry_fieldNotInVersion_throwsNotFound() {
        var rdo = rdo();
        mockVersion(version(rdo, field("name", DataType.TEXT, false)));

        var request = request(List.of(new EntryValueDto(UUID.randomUUID()).textValue("x")));

        assertThatThrownBy(() -> service.createEntry(OBJECT_ID, VERSION_ID, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateEntry_omittedField_clearsItsValue() {
        var rdo = rdo();
        var text = field("name", DataType.TEXT, false);
        var version = version(rdo, text);
        var entry = entry(rdo);
        entry.putValue(text).setTextValue("Vienna");
        mockVersion(version);
        when(referenceDataObjectRepository.existsById(OBJECT_ID)).thenReturn(true);
        when(entryRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        mockSave();

        var result = service.updateEntry(OBJECT_ID, VERSION_ID, entry.getId(), request(List.of()));

        assertThat(entry.getValues()).isEmpty();
        assertThat(result.getValues().getFirst().getTextValue()).isNull();
    }

    @Test
    void updateEntry_leavesValuesOfFieldsOutsideTheVersionUntouched() {
        var rdo = rdo();
        var inVersion = field("name", DataType.TEXT, false);
        var otherVersionField = field("legacy", DataType.TEXT, false);
        var entry = entry(rdo);
        entry.putValue(otherVersionField).setTextValue("kept");
        mockVersion(version(rdo, inVersion));
        when(referenceDataObjectRepository.existsById(OBJECT_ID)).thenReturn(true);
        when(entryRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        mockSave();

        service.updateEntry(OBJECT_ID, VERSION_ID, entry.getId(), request(List.of(
                new EntryValueDto(inVersion.getId()).textValue("Vienna"))));

        assertThat(entry.findValue(otherVersionField.getId())).isPresent();
        assertThat(entry.findValue(otherVersionField.getId()).orElseThrow().getTextValue()).isEqualTo("kept");
    }

    @Test
    void deleteEntry_ofAnotherObject_throwsNotFound() {
        var otherRdo = new ReferenceDataObject("Other", "desc");
        ReflectionTestUtils.setField(otherRdo, "id", UUID.randomUUID());
        var entry = entry(otherRdo);
        when(referenceDataObjectRepository.existsById(OBJECT_ID)).thenReturn(true);
        when(entryRepository.findById(entry.getId())).thenReturn(Optional.of(entry));

        var entryId = entry.getId();

        assertThatThrownBy(() -> service.deleteEntry(OBJECT_ID, entryId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createEntry_mandatoryFieldOfAnotherNation_doesNotBlockCompleteness() {
        var rdo = rdo();
        var shared = field("name", DataType.TEXT, true);
        var frenchOnly = field("iban", DataType.TEXT, true, energy.eddie.s3.models.referencedata.Nation.FRA);
        mockVersion(version(rdo, shared, frenchOnly));
        mockSave();

        var result = service.createEntry(OBJECT_ID, VERSION_ID, request(List.of(
                new EntryValueDto(shared.getId()).textValue("Vienna"))));

        assertThat(result.getNation()).isEqualTo(Nation.AUT);
        assertThat(result.getComplete()).isTrue();
    }

    @Test
    void listEntries_projectsStoredValuesOntoTheRequestedVersion() {
        var rdo = rdo();
        var v1Field = field("name", DataType.TEXT, true);
        var v2Field = field("country", DataType.TEXT, true);
        var entry = entry(rdo);
        entry.putValue(v1Field).setTextValue("Vienna");
        mockVersion(version(rdo, v1Field, v2Field));
        when(entryRepository.findByReferenceDataObjectIdOrderByCreatedAtAsc(OBJECT_ID)).thenReturn(List.of(entry));

        var result = service.listEntries(OBJECT_ID, VERSION_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getComplete()).isFalse();
        assertThat(result.getFirst().getValues()).extracting(EntryValueDto::getTextValue)
                .containsExactly("Vienna", null);
    }
}
