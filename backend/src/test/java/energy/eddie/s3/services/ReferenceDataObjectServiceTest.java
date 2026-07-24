package energy.eddie.s3.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import energy.eddie.s3.exceptions.ConflictException;
import energy.eddie.s3.exceptions.NotFoundException;
import energy.eddie.s3.generated.model.CreateFieldRequest;
import energy.eddie.s3.generated.model.CreateReferenceDataObjectRequest;
import energy.eddie.s3.generated.model.FieldDto;
import energy.eddie.s3.generated.model.ReferenceDataObjectDetail;
import energy.eddie.s3.generated.model.ReferenceDataObjectVersionDetail;
import energy.eddie.s3.generated.model.ReplaceVersionFieldsRequest;
import energy.eddie.s3.generated.model.VersionFieldRequest;
import energy.eddie.s3.mappers.ReferenceDataObjectMapper;
import energy.eddie.s3.models.referencedata.DataType;
import energy.eddie.s3.models.referencedata.EnumOption;
import energy.eddie.s3.models.referencedata.Field;
import energy.eddie.s3.models.referencedata.Nation;
import energy.eddie.s3.models.referencedata.PublishState;
import energy.eddie.s3.models.referencedata.ReferenceDataObject;
import energy.eddie.s3.models.referencedata.ReferenceDataObjectVersion;
import energy.eddie.s3.repositories.EntryRepository;
import energy.eddie.s3.repositories.EntryValueRepository;
import energy.eddie.s3.repositories.FieldRepository;
import energy.eddie.s3.repositories.ReferenceDataObjectRepository;
import energy.eddie.s3.repositories.ReferenceDataObjectVersionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReferenceDataObjectServiceTest {

    @Mock
    private ReferenceDataObjectRepository referenceDataObjectRepository;
    @Mock
    private ReferenceDataObjectVersionRepository versionRepository;
    @Mock
    private FieldRepository fieldRepository;
    @Mock
    private EntryRepository entryRepository;
    @Mock
    private EntryValueRepository entryValueRepository;
    @Mock
    private ReferenceDataObjectMapper mapper;

    @InjectMocks
    private ReferenceDataObjectService service;

    private static ReferenceDataObject rdoWithId(UUID id) {
        var rdo = new ReferenceDataObject("name", "description");
        ReflectionTestUtils.setField(rdo, "id", id);
        return rdo;
    }

    private static ReferenceDataObjectVersion versionWithId(
            ReferenceDataObject rdo, UUID versionId, int code, PublishState state) {
        var version = new ReferenceDataObjectVersion(rdo, code, state);
        ReflectionTestUtils.setField(version, "id", versionId);
        return version;
    }

    private static Field fieldWithId(UUID id) {
        var field = new Field("price", DataType.NUMBER, true, null);
        ReflectionTestUtils.setField(field, "id", id);
        return field;
    }

    @Test
    void create_persistsRdoWithInitialDraftVersion() {
        var request = new CreateReferenceDataObjectRequest().name("Tariffs").description("desc");
        when(mapper.toDetail(any())).thenReturn(new ReferenceDataObjectDetail());
        when(referenceDataObjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(request);

        var captor = ArgumentCaptor.forClass(ReferenceDataObject.class);
        verify(referenceDataObjectRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Tariffs");
        assertThat(saved.getVersions()).hasSize(1);
        var version = saved.getVersions().get(0);
        assertThat(version.getVersionCode()).isEqualTo(1);
        assertThat(version.getPublishState()).isEqualTo(PublishState.DRAFT);
    }

    @Test
    void get_missing_throwsNotFound() {
        var id = UUID.randomUUID();
        when(referenceDataObjectRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_withFields_throwsConflict() {
        var id = UUID.randomUUID();
        var rdo = rdoWithId(id);
        var version = versionWithId(rdo, UUID.randomUUID(), 1, PublishState.DRAFT);
        version.getFields().add(fieldWithId(UUID.randomUUID()));
        rdo.getVersions().add(version);
        when(referenceDataObjectRepository.findById(id)).thenReturn(Optional.of(rdo));

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ConflictException.class);
        verify(referenceDataObjectRepository, never()).delete(any());
    }

    @Test
    void delete_withoutFields_deletes() {
        var id = UUID.randomUUID();
        var rdo = rdoWithId(id);
        rdo.getVersions().add(versionWithId(rdo, UUID.randomUUID(), 1, PublishState.DRAFT));
        when(referenceDataObjectRepository.findById(id)).thenReturn(Optional.of(rdo));

        service.delete(id);

        verify(referenceDataObjectRepository).delete(rdo);
    }

    @Test
    void createVersion_incrementsCodeAndCopiesFields() {
        var id = UUID.randomUUID();
        var rdo = rdoWithId(id);
        var latest = versionWithId(rdo, UUID.randomUUID(), 3, PublishState.PUBLISHED);
        var field = fieldWithId(UUID.randomUUID());
        latest.getFields().add(field);
        when(referenceDataObjectRepository.findById(id)).thenReturn(Optional.of(rdo));
        when(versionRepository.findFirstByReferenceDataObjectIdOrderByVersionCodeDesc(id))
                .thenReturn(Optional.of(latest));
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toVersionDetail(any())).thenReturn(new ReferenceDataObjectVersionDetail());

        service.createVersion(id);

        var captor = ArgumentCaptor.forClass(ReferenceDataObjectVersion.class);
        verify(versionRepository).save(captor.capture());
        var created = captor.getValue();
        assertThat(created.getVersionCode()).isEqualTo(4);
        assertThat(created.getPublishState()).isEqualTo(PublishState.DRAFT);
        assertThat(created.getFields()).containsExactly(field);
    }

    @Test
    void createVersion_copiesEnumFieldsWithOptions() {
        var id = UUID.randomUUID();
        var rdo = rdoWithId(id);
        var latest = versionWithId(rdo, UUID.randomUUID(), 1, PublishState.PUBLISHED);
        var field = new Field("role", DataType.ENUM, true, null);
        ReflectionTestUtils.setField(field, "id", UUID.randomUUID());
        field.addOption("DSO");
        latest.getFields().add(field);
        when(referenceDataObjectRepository.findById(id)).thenReturn(Optional.of(rdo));
        when(versionRepository.findFirstByReferenceDataObjectIdOrderByVersionCodeDesc(id))
                .thenReturn(Optional.of(latest));
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toVersionDetail(any())).thenReturn(new ReferenceDataObjectVersionDetail());

        service.createVersion(id);

        var captor = ArgumentCaptor.forClass(ReferenceDataObjectVersion.class);
        verify(versionRepository).save(captor.capture());
        assertThat(captor.getValue().getFields().get(0).getOptions())
                .extracting(EnumOption::getName)
                .containsExactly("DSO");
    }

    @Test
    void publishVersion_setsPublished() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var rdo = rdoWithId(id);
        var version = versionWithId(rdo, versionId, 1, PublishState.DRAFT);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toVersionDetail(any())).thenReturn(new ReferenceDataObjectVersionDetail());

        service.publishVersion(id, versionId);

        assertThat(version.getPublishState()).isEqualTo(PublishState.PUBLISHED);
    }

    @Test
    void publishVersion_wrongRdo_throwsNotFound() {
        var versionId = UUID.randomUUID();
        var version = versionWithId(rdoWithId(UUID.randomUUID()), versionId, 1, PublishState.DRAFT);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.publishVersion(UUID.randomUUID(), versionId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createField_onDraft_linksField() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var rdo = rdoWithId(id);
        var version = versionWithId(rdo, versionId, 1, PublishState.DRAFT);
        var savedField = fieldWithId(UUID.randomUUID());
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(fieldRepository.save(any())).thenReturn(savedField);
        when(mapper.toFieldDto(savedField)).thenReturn(new FieldDto());

        var request = new CreateFieldRequest()
                .name("price")
                .dataType(energy.eddie.s3.generated.model.DataType.NUMBER)
                .mandatory(true);
        service.createField(id, versionId, request);

        assertThat(version.getFields()).containsExactly(savedField);
        verify(versionRepository).save(version);
    }

    @Test
    void createField_enumWithOptions_persistsOptionsAndNation() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var version = versionWithId(rdoWithId(id), versionId, 1, PublishState.DRAFT);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(fieldRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toFieldDto(any())).thenReturn(new FieldDto());

        var request = new CreateFieldRequest()
                .name("role")
                .dataType(energy.eddie.s3.generated.model.DataType.ENUM)
                .mandatory(true)
                .nation(energy.eddie.s3.generated.model.Nation.AUT)
                .options(List.of("DATA_HUB", "DSO"));
        service.createField(id, versionId, request);

        var captor = ArgumentCaptor.forClass(Field.class);
        verify(fieldRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getDataType()).isEqualTo(DataType.ENUM);
        assertThat(saved.getNation()).isEqualTo(Nation.AUT);
        assertThat(saved.getOptions()).extracting(EnumOption::getName)
                .containsExactly("DATA_HUB", "DSO");
        assertThat(saved.getOptions()).allSatisfy(option ->
                assertThat(option.getField()).isSameAs(saved));
    }

    @Test
    void createField_enumWithoutOptions_throwsConflict() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var version = versionWithId(rdoWithId(id), versionId, 1, PublishState.DRAFT);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        var request = new CreateFieldRequest()
                .name("role")
                .dataType(energy.eddie.s3.generated.model.DataType.ENUM)
                .mandatory(true);
        assertThatThrownBy(() -> service.createField(id, versionId, request))
                .isInstanceOf(ConflictException.class);
        verify(fieldRepository, never()).save(any());
    }

    @Test
    void createField_nonEnumWithOptions_throwsConflict() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var version = versionWithId(rdoWithId(id), versionId, 1, PublishState.DRAFT);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        var request = new CreateFieldRequest()
                .name("price")
                .dataType(energy.eddie.s3.generated.model.DataType.NUMBER)
                .mandatory(true)
                .options(List.of("DSO"));
        assertThatThrownBy(() -> service.createField(id, versionId, request))
                .isInstanceOf(ConflictException.class);
        verify(fieldRepository, never()).save(any());
    }

    @Test
    void createField_enumWithDuplicateOption_throwsConflict() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var version = versionWithId(rdoWithId(id), versionId, 1, PublishState.DRAFT);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        var request = new CreateFieldRequest()
                .name("role")
                .dataType(energy.eddie.s3.generated.model.DataType.ENUM)
                .mandatory(true)
                .options(List.of("DSO", "DSO"));
        assertThatThrownBy(() -> service.createField(id, versionId, request))
                .isInstanceOf(ConflictException.class);
        verify(fieldRepository, never()).save(any());
    }

    @Test
    void createField_enumWithBlankOption_throwsConflict() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var version = versionWithId(rdoWithId(id), versionId, 1, PublishState.DRAFT);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        var request = new CreateFieldRequest()
                .name("role")
                .dataType(energy.eddie.s3.generated.model.DataType.ENUM)
                .mandatory(true)
                .options(List.of("DSO", "  "));
        assertThatThrownBy(() -> service.createField(id, versionId, request))
                .isInstanceOf(ConflictException.class);
        verify(fieldRepository, never()).save(any());
    }

    @Test
    void createField_onPublished_throwsConflict() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var version = versionWithId(rdoWithId(id), versionId, 1, PublishState.PUBLISHED);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        var request = new CreateFieldRequest()
                .name("price")
                .dataType(energy.eddie.s3.generated.model.DataType.NUMBER)
                .mandatory(true);
        assertThatThrownBy(() -> service.createField(id, versionId, request))
                .isInstanceOf(ConflictException.class);
        verify(fieldRepository, never()).save(any());
    }

    @Test
    void unlinkField_orphan_deletesField() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var fieldId = UUID.randomUUID();
        var rdo = rdoWithId(id);
        var version = versionWithId(rdo, versionId, 1, PublishState.DRAFT);
        var field = fieldWithId(fieldId);
        version.getFields().add(field);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(versionRepository.countByFieldsId(fieldId)).thenReturn(0L);

        service.unlinkField(id, versionId, fieldId);

        assertThat(version.getFields()).isEmpty();
        verify(fieldRepository).delete(field);
    }

    @Test
    void delete_withEntries_throwsConflict() {
        var id = UUID.randomUUID();
        var rdo = rdoWithId(id);
        rdo.getVersions().add(versionWithId(rdo, UUID.randomUUID(), 1, PublishState.DRAFT));
        when(referenceDataObjectRepository.findById(id)).thenReturn(Optional.of(rdo));
        when(entryRepository.existsByReferenceDataObjectId(id)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ConflictException.class);
        verify(referenceDataObjectRepository, never()).delete(any());
    }

    @Test
    void unlinkField_withStoredEntryValues_keepsField() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var fieldId = UUID.randomUUID();
        var rdo = rdoWithId(id);
        var version = versionWithId(rdo, versionId, 1, PublishState.DRAFT);
        var field = fieldWithId(fieldId);
        version.getFields().add(field);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(versionRepository.countByFieldsId(fieldId)).thenReturn(0L);
        when(entryValueRepository.existsByFieldId(fieldId)).thenReturn(true);

        service.unlinkField(id, versionId, fieldId);

        assertThat(version.getFields()).isEmpty();
        verify(fieldRepository, never()).delete(any());
    }

    @Test
    void unlinkField_stillReferenced_keepsField() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var fieldId = UUID.randomUUID();
        var rdo = rdoWithId(id);
        var version = versionWithId(rdo, versionId, 2, PublishState.DRAFT);
        var field = fieldWithId(fieldId);
        version.getFields().add(field);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(versionRepository.countByFieldsId(fieldId)).thenReturn(1L);

        service.unlinkField(id, versionId, fieldId);

        verify(fieldRepository, never()).delete(any());
    }

    @Test
    void unlinkField_onPublished_throwsConflict() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var version = versionWithId(rdoWithId(id), versionId, 1, PublishState.PUBLISHED);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.unlinkField(id, versionId, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unlinkField_notLinked_throwsNotFound() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var version = versionWithId(rdoWithId(id), versionId, 1, PublishState.DRAFT);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.unlinkField(id, versionId, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void replaceVersionFields_keepsExistingAndCreatesNew() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var keptId = UUID.randomUUID();
        var rdo = rdoWithId(id);
        var version = versionWithId(rdo, versionId, 1, PublishState.DRAFT);
        var kept = fieldWithId(keptId);
        version.getFields().add(kept);
        var newField = fieldWithId(UUID.randomUUID());
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(fieldRepository.findById(keptId)).thenReturn(Optional.of(kept));
        when(fieldRepository.save(any())).thenReturn(newField);
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toVersionDetail(any())).thenReturn(new ReferenceDataObjectVersionDetail());

        var request = new ReplaceVersionFieldsRequest()
                .addFieldsItem(new VersionFieldRequest().id(keptId))
                .addFieldsItem(new VersionFieldRequest()
                        .name("volume")
                        .dataType(energy.eddie.s3.generated.model.DataType.NUMBER)
                        .mandatory(false));
        service.replaceVersionFields(id, versionId, request);

        assertThat(version.getFields()).containsExactly(kept, newField);
        verify(fieldRepository, never()).delete(any());
    }

    @Test
    void replaceVersionFields_newEnumField_carriesOptions() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var version = versionWithId(rdoWithId(id), versionId, 1, PublishState.DRAFT);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(fieldRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toVersionDetail(any())).thenReturn(new ReferenceDataObjectVersionDetail());

        var request = new ReplaceVersionFieldsRequest()
                .addFieldsItem(new VersionFieldRequest()
                        .name("role")
                        .dataType(energy.eddie.s3.generated.model.DataType.ENUM)
                        .mandatory(true)
                        .options(List.of("DATA_HUB", "DSO")));
        service.replaceVersionFields(id, versionId, request);

        assertThat(version.getFields()).hasSize(1);
        assertThat(version.getFields().get(0).getOptions()).extracting(EnumOption::getName)
                .containsExactly("DATA_HUB", "DSO");
    }

    @Test
    void replaceVersionFields_newEnumFieldWithoutOptions_throwsConflict() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var version = versionWithId(rdoWithId(id), versionId, 1, PublishState.DRAFT);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        var request = new ReplaceVersionFieldsRequest()
                .addFieldsItem(new VersionFieldRequest()
                        .name("role")
                        .dataType(energy.eddie.s3.generated.model.DataType.ENUM)
                        .mandatory(true));
        assertThatThrownBy(() -> service.replaceVersionFields(id, versionId, request))
                .isInstanceOf(ConflictException.class);
        verify(fieldRepository, never()).save(any());
    }

    @Test
    void replaceVersionFields_removedOrphan_deletesField() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var removedId = UUID.randomUUID();
        var rdo = rdoWithId(id);
        var version = versionWithId(rdo, versionId, 1, PublishState.DRAFT);
        var removed = fieldWithId(removedId);
        version.getFields().add(removed);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepository.countByFieldsId(removedId)).thenReturn(0L);
        when(mapper.toVersionDetail(any())).thenReturn(new ReferenceDataObjectVersionDetail());

        service.replaceVersionFields(id, versionId, new ReplaceVersionFieldsRequest());

        assertThat(version.getFields()).isEmpty();
        verify(fieldRepository).delete(removed);
    }

    @Test
    void replaceVersionFields_removedStillReferenced_keepsField() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var removedId = UUID.randomUUID();
        var rdo = rdoWithId(id);
        var version = versionWithId(rdo, versionId, 2, PublishState.DRAFT);
        var removed = fieldWithId(removedId);
        version.getFields().add(removed);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepository.countByFieldsId(removedId)).thenReturn(1L);
        when(mapper.toVersionDetail(any())).thenReturn(new ReferenceDataObjectVersionDetail());

        service.replaceVersionFields(id, versionId, new ReplaceVersionFieldsRequest());

        verify(fieldRepository, never()).delete(any());
    }

    @Test
    void replaceVersionFields_unknownId_throwsNotFound() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var unknownId = UUID.randomUUID();
        var version = versionWithId(rdoWithId(id), versionId, 1, PublishState.DRAFT);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(fieldRepository.findById(unknownId)).thenReturn(Optional.empty());

        var request = new ReplaceVersionFieldsRequest()
                .addFieldsItem(new VersionFieldRequest().id(unknownId));
        assertThatThrownBy(() -> service.replaceVersionFields(id, versionId, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void replaceVersionFields_newFieldMissingProps_throwsConflict() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var version = versionWithId(rdoWithId(id), versionId, 1, PublishState.DRAFT);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        var request = new ReplaceVersionFieldsRequest()
                .addFieldsItem(new VersionFieldRequest().name("incomplete"));
        assertThatThrownBy(() -> service.replaceVersionFields(id, versionId, request))
                .isInstanceOf(ConflictException.class);
        verify(fieldRepository, never()).save(any());
    }

    @Test
    void replaceVersionFields_onPublished_throwsConflict() {
        var id = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var version = versionWithId(rdoWithId(id), versionId, 1, PublishState.PUBLISHED);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        assertThatThrownBy(() ->
                service.replaceVersionFields(id, versionId, new ReplaceVersionFieldsRequest()))
                .isInstanceOf(ConflictException.class);
        verify(versionRepository, never()).save(any());
    }

    @Test
    void replaceVersionFields_wrongRdo_throwsNotFound() {
        var versionId = UUID.randomUUID();
        var version = versionWithId(rdoWithId(UUID.randomUUID()), versionId, 1, PublishState.DRAFT);
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.replaceVersionFields(
                UUID.randomUUID(), versionId, new ReplaceVersionFieldsRequest()))
                .isInstanceOf(NotFoundException.class);
    }
}
