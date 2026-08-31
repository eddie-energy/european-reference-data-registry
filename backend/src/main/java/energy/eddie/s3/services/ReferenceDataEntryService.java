package energy.eddie.s3.services;

import energy.eddie.s3.exceptions.ConflictException;
import energy.eddie.s3.exceptions.ForbiddenException;
import energy.eddie.s3.exceptions.NotFoundException;
import energy.eddie.s3.generated.model.ReferenceDataEntryDto;
import energy.eddie.s3.generated.model.ReferenceDataEntryValueDto;
import energy.eddie.s3.generated.model.UpsertReferenceDataEntryRequest;
import energy.eddie.s3.models.referencedata.EnumOption;
import energy.eddie.s3.models.referencedata.Field;
import energy.eddie.s3.models.referencedata.Nation;
import energy.eddie.s3.models.referencedata.PublishState;
import energy.eddie.s3.models.referencedata.ReferenceDataEntry;
import energy.eddie.s3.models.referencedata.ReferenceDataEntryValue;
import energy.eddie.s3.models.referencedata.ReferenceDataObjectVersion;
import energy.eddie.s3.repositories.ReferenceDataEntryRepository;
import energy.eddie.s3.repositories.ReferenceDataObjectRepository;
import energy.eddie.s3.repositories.ReferenceDataObjectVersionRepository;
import energy.eddie.s3.security.CurrentUser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferenceDataEntryService {

    private final ReferenceDataObjectRepository referenceDataObjectRepository;
    private final ReferenceDataObjectVersionRepository versionRepository;
    private final ReferenceDataEntryRepository referenceDataEntryRepository;
    private final CurrentUser currentUser;

    public ReferenceDataEntryService(
            ReferenceDataObjectRepository referenceDataObjectRepository,
            ReferenceDataObjectVersionRepository versionRepository,
            ReferenceDataEntryRepository referenceDataEntryRepository,
            CurrentUser currentUser) {
        this.referenceDataObjectRepository = referenceDataObjectRepository;
        this.versionRepository = versionRepository;
        this.referenceDataEntryRepository = referenceDataEntryRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<ReferenceDataEntryDto> listReferenceDataEntries(UUID id, UUID versionId) {
        var version = findVersion(id, versionId);
        var allVersionsDesc = versionRepository.findByReferenceDataObjectIdOrderByVersionCodeDesc(id);
        return referenceDataEntryRepository.findByReferenceDataObjectIdOrderByCreatedAtAsc(id).stream()
                .map(referenceDataEntry -> toDto(referenceDataEntry, version, allVersionsDesc))
                .toList();
    }

    @Transactional
    public ReferenceDataEntryDto createReferenceDataEntry(
            UUID id, UUID versionId, UpsertReferenceDataEntryRequest request) {
        var version = findVersion(id, versionId);
        var nation = toNation(request.getNation());
        requireMaintainer(nation);
        var referenceDataEntry = new ReferenceDataEntry(version.getReferenceDataObject(), nation);
        applyValues(referenceDataEntry, version, request);
        var saved = referenceDataEntryRepository.save(referenceDataEntry);
        return toDto(saved, version, versionRepository.findByReferenceDataObjectIdOrderByVersionCodeDesc(id));
    }

    @Transactional
    public ReferenceDataEntryDto updateReferenceDataEntry(
            UUID id, UUID versionId, UUID referenceDataEntryId, UpsertReferenceDataEntryRequest request) {
        var version = findVersion(id, versionId);
        var referenceDataEntry = findReferenceDataEntry(id, referenceDataEntryId);
        var nation = toNation(request.getNation());
        requireMaintainer(referenceDataEntry.getNation());
        requireMaintainer(nation);
        referenceDataEntry.setNation(nation);
        applyValues(referenceDataEntry, version, request);
        referenceDataEntry.touch();
        var saved = referenceDataEntryRepository.save(referenceDataEntry);
        return toDto(saved, version, versionRepository.findByReferenceDataObjectIdOrderByVersionCodeDesc(id));
    }

    @Transactional
    public void deleteReferenceDataEntry(UUID id, UUID referenceDataEntryId) {
        var referenceDataEntry = findReferenceDataEntry(id, referenceDataEntryId);
        requireMaintainer(referenceDataEntry.getNation());
        referenceDataEntryRepository.delete(referenceDataEntry);
    }

    private void requireMaintainer(@Nullable Nation nation) {
        if (!currentUser.mayMaintainReferenceDataEntriesFor(nation)) {
            throw new ForbiddenException("You are not an NDSF for nation " + nation);
        }
    }

    private void applyValues(
            ReferenceDataEntry referenceDataEntry,
            ReferenceDataObjectVersion version,
            UpsertReferenceDataEntryRequest request) {
        Map<UUID, Field> versionFields = new HashMap<>();
        version.getFields().forEach(field -> versionFields.put(field.getId(), field));

        var submitted = request.getValues();
        for (var dto : submitted) {
            var field = versionFields.get(dto.getFieldId());
            if (field == null) {
                throw new NotFoundException(
                        "Field " + dto.getFieldId() + " is not linked to version " + version.getId());
            }
            if (isEmpty(dto)) {
                referenceDataEntry.removeValue(field.getId());
                continue;
            }
            var value = referenceDataEntry.putValue(field);
            value.clear();
            assign(value, field, dto);
        }

        var submittedIds = submitted.stream().map(ReferenceDataEntryValueDto::getFieldId).toList();
        versionFields.keySet().stream()
                .filter(fieldId -> !submittedIds.contains(fieldId))
                .toList()
                .forEach(referenceDataEntry::removeValue);
    }

    private static void assign(ReferenceDataEntryValue value, Field field, ReferenceDataEntryValueDto dto) {
        switch (field.getDataType()) {
            case TEXT -> value.setTextValue(requireOnly(dto, field, dto.getTextValue()));
            case NUMBER -> value.setNumberValue(requireOnly(dto, field, dto.getNumberValue()));
            case DATE -> value.setDateValue(requireOnly(dto, field, dto.getDateValue()));
            case ENUM -> value.setEnumOption(findOption(field, requireOnly(dto, field, dto.getEnumOptionId())));
        }
    }

    private static EnumOption findOption(Field field, UUID optionId) {
        return field.getOptions().stream()
                .filter(option -> option.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new ConflictException(
                        "Enum option " + optionId + " does not belong to field " + field.getId()));
    }

    private static <T> T requireOnly(ReferenceDataEntryValueDto dto, Field field, @Nullable T expectedSlot) {
        if (expectedSlot == null || setSlots(dto) != 1) {
            throw new ConflictException(
                    "Value for field " + field.getId() + " must match data type " + field.getDataType());
        }
        return expectedSlot;
    }

    private static boolean isEmpty(ReferenceDataEntryValueDto dto) {
        return setSlots(dto) == 0;
    }

    private static int setSlots(ReferenceDataEntryValueDto dto) {
        var count = 0;
        if (dto.getTextValue() != null) {
            count++;
        }
        if (dto.getNumberValue() != null) {
            count++;
        }
        if (dto.getDateValue() != null) {
            count++;
        }
        if (dto.getEnumOptionId() != null) {
            count++;
        }
        return count;
    }

    private static ReferenceDataEntryDto toDto(
            ReferenceDataEntry referenceDataEntry,
            ReferenceDataObjectVersion version,
            List<ReferenceDataObjectVersion> allVersionsDesc) {
        var values = version.getFields().stream()
                .map(field -> toValueDto(referenceDataEntry, field))
                .toList();
        var complete = isComplete(referenceDataEntry, version);
        var dto = new ReferenceDataEntryDto(
                referenceDataEntry.getId(),
                referenceDataEntry.getCreatedAt(),
                referenceDataEntry.getUpdatedAt(),
                complete,
                values);
        dto.setNation(fromNation(referenceDataEntry.getNation()));
        dto.setLastCompleteVersionCode(findLastCompleteVersionCode(referenceDataEntry, allVersionsDesc));
        return dto;
    }

    private static boolean isComplete(ReferenceDataEntry referenceDataEntry, ReferenceDataObjectVersion version) {
        return version.getFields().stream()
                .filter(Field::isMandatory)
                .filter(field -> appliesTo(field, referenceDataEntry.getNation()))
                .allMatch(field -> referenceDataEntry.findValue(field.getId()).isPresent());
    }

    @Nullable
    private static Integer findLastCompleteVersionCode(
            ReferenceDataEntry referenceDataEntry, List<ReferenceDataObjectVersion> allVersionsDesc) {
        return allVersionsDesc.stream()
                .filter(version -> isComplete(referenceDataEntry, version))
                .map(ReferenceDataObjectVersion::getVersionCode)
                .findFirst()
                .orElse(null);
    }

    private static boolean appliesTo(Field field, @Nullable Nation referenceDataEntryNation) {
        return field.getNation() == null || field.getNation() == referenceDataEntryNation;
    }

    @Nullable
    private static Nation toNation(@Nullable energy.eddie.s3.generated.model.Nation nation) {
        return nation == null ? null : Nation.valueOf(nation.name());
    }

    @Nullable
    private static energy.eddie.s3.generated.model.Nation fromNation(@Nullable Nation nation) {
        return nation == null ? null : energy.eddie.s3.generated.model.Nation.valueOf(nation.name());
    }

    private static ReferenceDataEntryValueDto toValueDto(ReferenceDataEntry referenceDataEntry, Field field) {
        var dto = new ReferenceDataEntryValueDto(field.getId());
        referenceDataEntry.findValue(field.getId()).ifPresent(value -> {
            dto.setTextValue(value.getTextValue());
            dto.setNumberValue(value.getNumberValue());
            dto.setDateValue(value.getDateValue());
            var option = value.getEnumOption();
            dto.setEnumOptionId(option == null ? null : option.getId());
        });
        return dto;
    }

    private ReferenceDataObjectVersion findVersion(UUID id, UUID versionId) {
        var version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Version " + versionId + " not found"));
        if (!version.getReferenceDataObject().getId().equals(id)) {
            throw new NotFoundException("Version " + versionId + " does not belong to reference data object " + id);
        }
        if (version.getPublishState() != PublishState.PUBLISHED && !currentUser.maySeeDrafts()) {
            throw new NotFoundException("Version " + versionId + " not found");
        }
        return version;
    }

    private ReferenceDataEntry findReferenceDataEntry(UUID id, UUID referenceDataEntryId) {
        if (!referenceDataObjectRepository.existsById(id)) {
            throw new NotFoundException("Reference data object " + id + " not found");
        }
        var referenceDataEntry = referenceDataEntryRepository
                .findById(referenceDataEntryId)
                .orElseThrow(() ->
                        new NotFoundException("Reference data entry " + referenceDataEntryId + " not found"));
        if (!referenceDataEntry.getReferenceDataObject().getId().equals(id)) {
            throw new NotFoundException("Reference data entry " + referenceDataEntryId
                    + " does not belong to reference data object " + id);
        }
        return referenceDataEntry;
    }
}
