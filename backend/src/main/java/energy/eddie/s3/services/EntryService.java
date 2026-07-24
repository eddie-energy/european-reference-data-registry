package energy.eddie.s3.services;

import energy.eddie.s3.exceptions.ConflictException;
import energy.eddie.s3.exceptions.NotFoundException;
import energy.eddie.s3.generated.model.EntryDto;
import energy.eddie.s3.generated.model.EntryValueDto;
import energy.eddie.s3.generated.model.UpsertEntryRequest;
import energy.eddie.s3.models.referencedata.Entry;
import energy.eddie.s3.models.referencedata.EntryValue;
import energy.eddie.s3.models.referencedata.EnumOption;
import energy.eddie.s3.models.referencedata.Field;
import energy.eddie.s3.models.referencedata.Nation;
import energy.eddie.s3.models.referencedata.ReferenceDataObjectVersion;
import energy.eddie.s3.repositories.EntryRepository;
import energy.eddie.s3.repositories.ReferenceDataObjectRepository;
import energy.eddie.s3.repositories.ReferenceDataObjectVersionRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entries belong to a reference data object, not to a single version: an entry created under
 * version 1 stays available in every later version. Reads project the stored values onto the
 * field set of the requested version.
 */
@Service
public class EntryService {

    private final ReferenceDataObjectRepository referenceDataObjectRepository;
    private final ReferenceDataObjectVersionRepository versionRepository;
    private final EntryRepository entryRepository;

    public EntryService(
            ReferenceDataObjectRepository referenceDataObjectRepository,
            ReferenceDataObjectVersionRepository versionRepository,
            EntryRepository entryRepository) {
        this.referenceDataObjectRepository = referenceDataObjectRepository;
        this.versionRepository = versionRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional(readOnly = true)
    public List<EntryDto> listEntries(UUID id, UUID versionId) {
        var version = findVersion(id, versionId);
        return entryRepository.findByReferenceDataObjectIdOrderByCreatedAtAsc(id).stream()
                .map(entry -> toDto(entry, version))
                .toList();
    }

    @Transactional
    public EntryDto createEntry(UUID id, UUID versionId, UpsertEntryRequest request) {
        var version = findVersion(id, versionId);
        var entry = new Entry(version.getReferenceDataObject(), toNation(request.getNation()));
        applyValues(entry, version, request);
        return toDto(entryRepository.save(entry), version);
    }

    @Transactional
    public EntryDto updateEntry(UUID id, UUID versionId, UUID entryId, UpsertEntryRequest request) {
        var version = findVersion(id, versionId);
        var entry = findEntry(id, entryId);
        entry.setNation(toNation(request.getNation()));
        applyValues(entry, version, request);
        entry.touch();
        return toDto(entryRepository.save(entry), version);
    }

    @Transactional
    public void deleteEntry(UUID id, UUID entryId) {
        entryRepository.delete(findEntry(id, entryId));
    }

    /**
     * Replaces the values the entry holds for the fields of the given version. Fields of other
     * versions are left untouched, fields of this version missing from the request are cleared.
     */
    private void applyValues(Entry entry, ReferenceDataObjectVersion version, UpsertEntryRequest request) {
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
                entry.removeValue(field.getId());
                continue;
            }
            var value = entry.putValue(field);
            value.clear();
            assign(value, field, dto);
        }

        var submittedIds = submitted.stream().map(EntryValueDto::getFieldId).toList();
        versionFields.keySet().stream()
                .filter(fieldId -> !submittedIds.contains(fieldId))
                .toList()
                .forEach(entry::removeValue);
    }

    private static void assign(EntryValue value, Field field, EntryValueDto dto) {
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

    /**
     * The slot matching the field's data type must be set and it must be the only one.
     */
    private static <T> T requireOnly(EntryValueDto dto, Field field, @Nullable T expectedSlot) {
        if (expectedSlot == null || setSlots(dto) != 1) {
            throw new ConflictException(
                    "Value for field " + field.getId() + " must match data type " + field.getDataType());
        }
        return expectedSlot;
    }

    private static boolean isEmpty(EntryValueDto dto) {
        return setSlots(dto) == 0;
    }

    private static int setSlots(EntryValueDto dto) {
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

    private static EntryDto toDto(Entry entry, ReferenceDataObjectVersion version) {
        var values = version.getFields().stream()
                .map(field -> toValueDto(entry, field))
                .toList();
        var complete = version.getFields().stream()
                .filter(Field::isMandatory)
                .filter(field -> appliesTo(field, entry.getNation()))
                .allMatch(field -> entry.findValue(field.getId()).isPresent());
        var dto = new EntryDto(entry.getId(), entry.getCreatedAt(), entry.getUpdatedAt(), complete, values);
        dto.setNation(fromNation(entry.getNation()));
        return dto;
    }

    private static boolean appliesTo(Field field, @Nullable Nation entryNation) {
        return field.getNation() == null || field.getNation() == entryNation;
    }

    @Nullable
    private static Nation toNation(@Nullable energy.eddie.s3.generated.model.Nation nation) {
        return nation == null ? null : Nation.valueOf(nation.name());
    }

    @Nullable
    private static energy.eddie.s3.generated.model.Nation fromNation(@Nullable Nation nation) {
        return nation == null ? null : energy.eddie.s3.generated.model.Nation.valueOf(nation.name());
    }

    private static EntryValueDto toValueDto(Entry entry, Field field) {
        var dto = new EntryValueDto(field.getId());
        entry.findValue(field.getId()).ifPresent(value -> {
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
        return version;
    }

    private Entry findEntry(UUID id, UUID entryId) {
        if (!referenceDataObjectRepository.existsById(id)) {
            throw new NotFoundException("Reference data object " + id + " not found");
        }
        var entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new NotFoundException("Entry " + entryId + " not found"));
        if (!entry.getReferenceDataObject().getId().equals(id)) {
            throw new NotFoundException("Entry " + entryId + " does not belong to reference data object " + id);
        }
        return entry;
    }
}
