package energy.eddie.s3.mappers;

import energy.eddie.s3.generated.model.EnumOptionDto;
import energy.eddie.s3.generated.model.FieldDto;
import energy.eddie.s3.generated.model.ReferenceDataObjectDetail;
import energy.eddie.s3.generated.model.ReferenceDataObjectVersionDetail;
import energy.eddie.s3.models.referencedata.EnumOption;
import energy.eddie.s3.models.referencedata.Field;
import energy.eddie.s3.models.referencedata.ReferenceDataObject;
import energy.eddie.s3.models.referencedata.ReferenceDataObjectVersion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReferenceDataObjectMapper {

    @Mapping(target = "buildingBlockUrl", ignore = true)
    ReferenceDataObjectDetail toDetail(ReferenceDataObject referenceDataObject);

    ReferenceDataObjectVersionDetail toVersionDetail(ReferenceDataObjectVersion version);

    FieldDto toFieldDto(Field field);

    EnumOptionDto toEnumOptionDto(EnumOption option);
}
