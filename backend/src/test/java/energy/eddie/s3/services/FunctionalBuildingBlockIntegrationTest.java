package energy.eddie.s3.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import energy.eddie.s3.config.PostgresTestConfiguration;
import energy.eddie.s3.generated.model.CreateReferenceDataObjectRequest;
import energy.eddie.s3.generated.model.FunctionalBuildingBlock;
import energy.eddie.s3.generated.model.UpdateReferenceDataObjectBuildingBlockRequest;
import energy.eddie.s3.repositories.ReferenceDataObjectRepository;
import energy.eddie.s3.security.CurrentUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.MediaType;
import energy.eddie.s3.security.CeedsRole;
import java.util.UUID;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
@Transactional
class FunctionalBuildingBlockIntegrationTest {
    @Autowired private ReferenceDataObjectService service;
    @Autowired private ReferenceDataObjectRepository objects;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @MockitoBean private CurrentUser currentUser;

    @BeforeEach
    void seeDrafts() {
        when(currentUser.maySeeDrafts()).thenReturn(true);
    }

    @Test
    void migration_seedsAllEightSharedLinks() {
        assertThat(jdbc.queryForList("SELECT code FROM functional_building_block", String.class))
                .containsExactlyInAnyOrder(
                        "ACCESS_TO_METERING_AND_CONSUMPTION_DATA", "ACCESS_TO_BASIC_MASTER_DATA",
                        "ACCESS_TO_IMPLICIT_FLEXIBILITY_SIGNALS", "CUSTOMER_SWITCHING",
                        "COLLECTIVE_SELF_CONSUMPTION_AND_ENERGY_COMMUNITIES", "FLEXIBLE_CONNECTION_AGREEMENTS",
                        "DEMAND_RESPONSE_PROCEDURES", "FORECAST_AND_PLANNING_DATA");
        assertThat(jdbc.queryForObject(
                "SELECT url FROM functional_building_block WHERE code = 'FORECAST_AND_PLANNING_DATA'", String.class))
                .isEqualTo("https://architecture.insieme.energy/reference-models/generic-data-sharing/generic-data-sharing.html");
    }

    @Test
    void selectionAndSharedUrl_surviveDatabaseRoundTrip() {
        var created = service.create(new CreateReferenceDataObjectRequest().name("Switching").description("description")
                .buildingBlock(FunctionalBuildingBlock.CUSTOMER_SWITCHING));
        objects.flush();
        entityManager.clear();

        var loaded = service.get(created.getId());

        assertThat(loaded.getBuildingBlock()).isEqualTo(FunctionalBuildingBlock.CUSTOMER_SWITCHING);
        assertThat(loaded.getBuildingBlockUrl()).isEqualTo(
                "https://architecture.insieme.energy/reference-models/regulated-domain/"
                        + "T4-2_04-customer-switching/T4-2_04-customer-switching.html");
    }

    @Test
    void sqlUrlUpdate_isVisibleForEveryLinkedObjectOnNextRead() {
        var first = service.create(new CreateReferenceDataObjectRequest().name("First").description("description")
                .buildingBlock(FunctionalBuildingBlock.CUSTOMER_SWITCHING));
        var second = service.create(new CreateReferenceDataObjectRequest().name("Second").description("description")
                .buildingBlock(FunctionalBuildingBlock.CUSTOMER_SWITCHING));
        objects.flush();
        assertThat(service.get(first.getId()).getBuildingBlockUrl()).isNotBlank();

        jdbc.update("UPDATE functional_building_block SET url = ? WHERE code = ?",
                "https://example.org/new-switching", "CUSTOMER_SWITCHING");

        assertThat(service.get(first.getId()).getBuildingBlockUrl()).isEqualTo("https://example.org/new-switching");
        assertThat(service.get(second.getId()).getBuildingBlockUrl()).isEqualTo("https://example.org/new-switching");
        assertThat(service.getAll()).filteredOn(detail -> detail.getId().equals(first.getId())
                        || detail.getId().equals(second.getId()))
                .hasSize(2).allSatisfy(detail ->
                        assertThat(detail.getBuildingBlockUrl()).isEqualTo("https://example.org/new-switching"));
    }

    @Test
    void unassignedObjectsAndBlocksWithoutUrl_remainReadable() {
        var unassigned = service.create(new CreateReferenceDataObjectRequest().name("Unassigned").description("description"));
        var linked = service.create(new CreateReferenceDataObjectRequest().name("Linked").description("description")
                .buildingBlock(FunctionalBuildingBlock.CUSTOMER_SWITCHING));
        objects.flush();
        jdbc.update("UPDATE functional_building_block SET url = NULL WHERE code = 'CUSTOMER_SWITCHING'");

        assertThat(service.get(unassigned.getId()).getBuildingBlock()).isNull();
        assertThat(service.get(unassigned.getId()).getBuildingBlockUrl()).isNull();
        assertThat(service.get(linked.getId()).getBuildingBlock()).isEqualTo(FunctionalBuildingBlock.CUSTOMER_SWITCHING);
        assertThat(service.get(linked.getId()).getBuildingBlockUrl()).isNull();
    }
    @Test
    void changedAndClearedSelection_persistsWithoutChangingVersionsOrCategory() {
        var object = service.create(new CreateReferenceDataObjectRequest().name("Roles").description("description")
                .category(energy.eddie.s3.generated.model.ReferenceDataObjectCategory.ROLE)
                .buildingBlock(FunctionalBuildingBlock.CUSTOMER_SWITCHING));

        service.updateBuildingBlock(object.getId(), new UpdateReferenceDataObjectBuildingBlockRequest()
                .buildingBlock(FunctionalBuildingBlock.DEMAND_RESPONSE_PROCEDURES));
        objects.flush();
        entityManager.clear();
        assertThat(service.get(object.getId()).getBuildingBlock())
                .isEqualTo(FunctionalBuildingBlock.DEMAND_RESPONSE_PROCEDURES);
        assertThat(service.get(object.getId()).getBuildingBlockUrl()).contains("T4-4_02-ir-demand-response");

        service.updateBuildingBlock(object.getId(), new UpdateReferenceDataObjectBuildingBlockRequest());
        objects.flush();
        entityManager.clear();
        var cleared = service.get(object.getId());
        assertThat(cleared.getBuildingBlock()).isNull();
        assertThat(cleared.getBuildingBlockUrl()).isNull();
        assertThat(cleared.getCategory()).isEqualTo(energy.eddie.s3.generated.model.ReferenceDataObjectCategory.ROLE);
        assertThat(cleared.getVersions()).hasSize(1);
        assertThat(cleared.getVersions().get(0).getId()).isEqualTo(object.getVersions().get(0).getId());
    }

    @Test
    void api_updatesClearsAndReturnsPublicBuildingBlockMetadata() throws Exception {
        var object = service.create(new CreateReferenceDataObjectRequest().name("API roles").description("description"));
        var path = "/api/reference-data-objects/" + object.getId();
        service.publishVersion(object.getId(), object.getVersions().get(0).getId());
        when(currentUser.maySeeDrafts()).thenReturn(false);

        mvc.perform(put(path + "/building-block")
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buildingBlock\":\"CUSTOMER_SWITCHING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildingBlock").value("CUSTOMER_SWITCHING"));
        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildingBlock").value("CUSTOMER_SWITCHING"))
                .andExpect(jsonPath("$.buildingBlockUrl").value(
                        "https://architecture.insieme.energy/reference-models/regulated-domain/"
                                + "T4-2_04-customer-switching/T4-2_04-customer-switching.html"));

        for (var body : java.util.List.of("{\"buildingBlock\":null}", "{}")) {
            mvc.perform(put(path + "/building-block")
                            .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.buildingBlock").isEmpty())
                    .andExpect(jsonPath("$.buildingBlockUrl").isEmpty());
        }
        mvc.perform(put("/api/reference-data-objects/" + UUID.randomUUID() + "/building-block")
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

}
