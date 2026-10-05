package energy.eddie.s3.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import energy.eddie.s3.models.referencedata.Nation;
import energy.eddie.s3.models.referencedata.ReferenceDataObject;
import energy.eddie.s3.models.referencedata.Responsibility;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ResponsibilityRepositoryIntegrationTest {
    @Autowired ReferenceDataObjectRepository objects;
    @Autowired ResponsibilityRepository responsibilities;

    @Test
    void twoOrganizationsCanShareNationAndRemovalRevokesOnlyOne() {
        var object = objects.save(new ReferenceDataObject("Permissions", "description"));
        var eda = UUID.randomUUID();
        var other = UUID.randomUUID();
        responsibilities.saveAndFlush(new Responsibility(object, eda, Nation.AUT));
        responsibilities.saveAndFlush(new Responsibility(object, other, Nation.AUT));

        assertThat(responsibilities.existsByReferenceDataObjectIdAndOrganizationIdInAndNation(
                        object.getId(), Set.of(eda), Nation.AUT))
                .isTrue();
        responsibilities.deleteByReferenceDataObjectIdAndOrganizationIdAndNation(object.getId(), eda, Nation.AUT);
        responsibilities.flush();

        assertThat(responsibilities.existsByReferenceDataObjectIdAndOrganizationIdInAndNation(
                        object.getId(), Set.of(eda), Nation.AUT))
                .isFalse();
        assertThat(responsibilities.existsByReferenceDataObjectIdAndOrganizationIdInAndNation(
                        object.getId(), Set.of(other), Nation.AUT))
                .isTrue();
    }
}
