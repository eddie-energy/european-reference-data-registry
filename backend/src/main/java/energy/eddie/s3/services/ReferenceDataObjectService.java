package energy.eddie.s3.services;

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
import energy.eddie.s3.models.referencedata.Field;
import energy.eddie.s3.models.referencedata.Nation;
import energy.eddie.s3.models.referencedata.PublishState;
import energy.eddie.s3.models.referencedata.ReferenceDataObject;
import energy.eddie.s3.models.referencedata.ReferenceDataObjectVersion;
import energy.eddie.s3.repositories.FieldRepository;
import energy.eddie.s3.repositories.ReferenceDataObjectRepository;
import energy.eddie.s3.repositories.ReferenceDataObjectVersionRepository;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferenceDataObjectService {

    private final ReferenceDataObjectRepository referenceDataObjectRepository;
    private final ReferenceDataObjectVersionRepository versionRepository;
    private final FieldRepository fieldRepository;
    private final ReferenceDataObjectMapper mapper;

    public ReferenceDataObjectService(
            ReferenceDataObjectRepository referenceDataObjectRepository,
            ReferenceDataObjectVersionRepository versionRepository,
            FieldRepository fieldRepository,
            ReferenceDataObjectMapper mapper) {
        this.referenceDataObjectRepository = referenceDataObjectRepository;
        this.versionRepository = versionRepository;
        this.fieldRepository = fieldRepository;
        this.mapper = mapper;
    }

    @Transactional
    public ReferenceDataObjectDetail create(CreateReferenceDataObjectRequest request) {
        var rdo = new ReferenceDataObject(request.getName(), request.getDescription());
        var version = new ReferenceDataObjectVersion(rdo, 1, PublishState.DRAFT);
        rdo.getVersions().add(version);
        return mapper.toDetail(referenceDataObjectRepository.save(rdo));
    }

    @Transactional(readOnly = true)
    public List<ReferenceDataObjectDetail> getAll() {
        return referenceDataObjectRepository.findAll().stream()
                .map(mapper::toDetail)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReferenceDataObjectDetail get(UUID id) {
        return mapper.toDetail(findReferenceDataObject(id));
    }

    @Transactional
    public void delete(UUID id) {
        var rdo = findReferenceDataObject(id);
        boolean hasFields = rdo.getVersions().stream()
                .anyMatch(version -> !version.getFields().isEmpty());
        if (hasFields) {
            throw new ConflictException("Reference data object has fields and cannot be deleted");
        }
        referenceDataObjectRepository.delete(rdo);
    }

    @Transactional
    public ReferenceDataObjectVersionDetail createVersion(UUID id) {
        var rdo = findReferenceDataObject(id);
        var latest = versionRepository.findFirstByReferenceDataObjectIdOrderByVersionCodeDesc(id)
                .orElseThrow(() -> new NotFoundException("No existing version found for reference data object " + id));
        var version = new ReferenceDataObjectVersion(rdo, latest.getVersionCode() + 1, PublishState.DRAFT);
        version.getFields().addAll(latest.getFields());
        return mapper.toVersionDetail(versionRepository.save(version));
    }

    @Transactional
    public ReferenceDataObjectVersionDetail publishVersion(UUID id, UUID versionId) {
        var version = findVersion(id, versionId);
        version.setPublishState(PublishState.PUBLISHED);
        return mapper.toVersionDetail(versionRepository.save(version));
    }

    @Transactional
    public FieldDto createField(UUID id, UUID versionId, CreateFieldRequest request) {
        var version = findVersion(id, versionId);
        if (version.getPublishState() == PublishState.PUBLISHED) {
            throw new ConflictException("Cannot add fields to a published version");
        }
        var field = new Field(
                request.getName(),
                DataType.valueOf(request.getDataType().name()),
                Boolean.TRUE.equals(request.getMandatory()),
                toNation(request.getNation()));
        var saved = fieldRepository.save(field);
        version.getFields().add(saved);
        versionRepository.save(version);
        return mapper.toFieldDto(saved);
    }

    @Transactional
    public ReferenceDataObjectVersionDetail replaceVersionFields(
            UUID id, UUID versionId, ReplaceVersionFieldsRequest request) {
        var version = findVersion(id, versionId);
        if (version.getPublishState() == PublishState.PUBLISHED) {
            throw new ConflictException("Cannot change fields of a published version");
        }
        var previousFields = List.copyOf(version.getFields());
        var desired = request.getFields().stream()
                .map(this::resolveField)
                .toList();
        version.getFields().clear();
        version.getFields().addAll(desired);
        versionRepository.save(version);
        var desiredIds = desired.stream().map(Field::getId).toList();
        for (var field : previousFields) {
            if (!desiredIds.contains(field.getId()) && versionRepository.countByFieldsId(field.getId()) == 0) {
                fieldRepository.delete(field);
            }
        }
        return mapper.toVersionDetail(version);
    }

    private Field resolveField(VersionFieldRequest request) {
        if (request.getId() != null) {
            return fieldRepository.findById(request.getId())
                    .orElseThrow(() -> new NotFoundException("Field " + request.getId() + " not found"));
        }
        if (request.getName() == null || request.getDataType() == null || request.getMandatory() == null) {
            throw new ConflictException("New fields require name, dataType and mandatory");
        }
        var field = new Field(
                request.getName(),
                DataType.valueOf(request.getDataType().name()),
                Boolean.TRUE.equals(request.getMandatory()),
                toNation(request.getNation()));
        return fieldRepository.save(field);
    }

    @Transactional
    public void unlinkField(UUID id, UUID versionId, UUID fieldId) {
        var version = findVersion(id, versionId);
        if (version.getPublishState() == PublishState.PUBLISHED) {
            throw new ConflictException("Cannot unlink fields from a published version");
        }
        var field = version.getFields().stream()
                .filter(f -> f.getId().equals(fieldId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Field " + fieldId + " is not linked to version " + versionId));
        version.getFields().remove(field);
        versionRepository.save(version);
        if (versionRepository.countByFieldsId(fieldId) == 0) {
            fieldRepository.delete(field);
        }
    }

    private ReferenceDataObject findReferenceDataObject(UUID id) {
        return referenceDataObjectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Reference data object " + id + " not found"));
    }

    private ReferenceDataObjectVersion findVersion(UUID id, UUID versionId) {
        var version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Version " + versionId + " not found"));
        if (!version.getReferenceDataObject().getId().equals(id)) {
            throw new NotFoundException("Version " + versionId + " does not belong to reference data object " + id);
        }
        return version;
    }

    @Nullable
    private static Nation toNation(@Nullable energy.eddie.s3.generated.model.Nation nation) {
        return nation == null ? null : Nation.valueOf(nation.name());
    }
}
