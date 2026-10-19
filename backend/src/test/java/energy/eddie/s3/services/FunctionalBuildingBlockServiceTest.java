package energy.eddie.s3.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import energy.eddie.s3.generated.model.CreateReferenceDataObjectRequest;
import energy.eddie.s3.generated.model.FunctionalBuildingBlock;
import energy.eddie.s3.generated.model.UpdateReferenceDataObjectBuildingBlockRequest;
import energy.eddie.s3.models.referencedata.ReferenceDataObject;
import energy.eddie.s3.exceptions.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import energy.eddie.s3.mappers.ReferenceDataObjectMapper;
import energy.eddie.s3.repositories.FieldRepository;
import energy.eddie.s3.repositories.FunctionalBuildingBlockRepository;
import energy.eddie.s3.repositories.ReferenceDataEntryRepository;
import energy.eddie.s3.repositories.ReferenceDataEntryValueRepository;
import energy.eddie.s3.repositories.ReferenceDataObjectRepository;
import energy.eddie.s3.repositories.ReferenceDataObjectVersionRepository;
import energy.eddie.s3.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FunctionalBuildingBlockServiceTest {
    @Mock private FunctionalBuildingBlockRepository buildingBlockRepository;
    @Mock private ReferenceDataObjectRepository referenceDataObjectRepository;
    @Mock private ReferenceDataObjectVersionRepository versionRepository;
    @Mock private FieldRepository fieldRepository;
    @Mock private ReferenceDataEntryRepository referenceDataEntryRepository;
    @Mock private ReferenceDataEntryValueRepository referenceDataEntryValueRepository;
    @Mock private CurrentUser currentUser;
    @Mock private ResponsibilityService responsibilityService;
    @Spy private ReferenceDataObjectMapper mapper = Mappers.getMapper(ReferenceDataObjectMapper.class);
    @InjectMocks private ReferenceDataObjectService service;

    @Test
    void create_returnsSelectedBuildingBlock() {
        when(referenceDataObjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var request = new CreateReferenceDataObjectRequest()
                .name("Switching roles")
                .description("description")
                .buildingBlock(FunctionalBuildingBlock.CUSTOMER_SWITCHING);

        var result = service.create(request);

        assertThat(result.getBuildingBlock()).isEqualTo(FunctionalBuildingBlock.CUSTOMER_SWITCHING);
    }

    @Test
    void create_withoutBuildingBlock_keepsSelectionAndUrlEmpty() {
        when(referenceDataObjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.create(new CreateReferenceDataObjectRequest().name("Roles").description("description"));

        assertThat(result.getBuildingBlock()).isNull();
        assertThat(result.getBuildingBlockUrl()).isNull();
    }
    @Test
    void update_setsChangesAndClearsBuildingBlock() {
        var id = UUID.randomUUID();
        var object = new ReferenceDataObject("Roles", "description");
        when(referenceDataObjectRepository.findById(id)).thenReturn(Optional.of(object));

        var selected = service.updateBuildingBlock(id, new UpdateReferenceDataObjectBuildingBlockRequest()
                .buildingBlock(FunctionalBuildingBlock.CUSTOMER_SWITCHING));
        var changed = service.updateBuildingBlock(id, new UpdateReferenceDataObjectBuildingBlockRequest()
                .buildingBlock(FunctionalBuildingBlock.DEMAND_RESPONSE_PROCEDURES));
        var cleared = service.updateBuildingBlock(id, new UpdateReferenceDataObjectBuildingBlockRequest());

        assertThat(selected.getBuildingBlock()).isEqualTo(FunctionalBuildingBlock.CUSTOMER_SWITCHING);
        assertThat(changed.getBuildingBlock()).isEqualTo(FunctionalBuildingBlock.DEMAND_RESPONSE_PROCEDURES);
        assertThat(cleared.getBuildingBlock()).isNull();
        assertThat(cleared.getBuildingBlockUrl()).isNull();
        assertThat(object.getBuildingBlock()).isNull();
    }

    @Test
    void update_missingObject_throwsNotFound() {
        var id = UUID.randomUUID();
        when(referenceDataObjectRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateBuildingBlock(id, new UpdateReferenceDataObjectBuildingBlockRequest()))
                .isInstanceOf(NotFoundException.class);
    }

}
