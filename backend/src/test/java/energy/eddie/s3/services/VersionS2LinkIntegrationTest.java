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
import energy.eddie.s3.generated.model.ReferenceDataObjectDetail;
import energy.eddie.s3.generated.model.UpdateVersionS2LinkRequest;
import energy.eddie.s3.repositories.ReferenceDataObjectVersionRepository;
import energy.eddie.s3.security.CeedsRole;
import energy.eddie.s3.security.CurrentUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
@Transactional
class VersionS2LinkIntegrationTest {
    private static final String LINK = "https://s2.example.org/models/permission-administrator";

    @Autowired private ReferenceDataObjectService service;
    @Autowired private ReferenceDataObjectVersionRepository versions;
    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc mvc;
    @MockitoBean private CurrentUser currentUser;

    @BeforeEach
    void seeDrafts() {
        when(currentUser.maySeeDrafts()).thenReturn(true);
    }

    @Test
    void link_survivesDatabaseRoundTripAndIsCarriedIntoNewVersion() {
        var object = createObject();
        var firstVersionId = object.getVersions().get(0).getId();
        service.updateVersionS2Link(object.getId(), firstVersionId, new UpdateVersionS2LinkRequest().s2Link(LINK));
        service.publishVersion(object.getId(), firstVersionId);
        versions.flush();
        entityManager.clear();

        var next = service.createVersion(object.getId());
        versions.flush();
        entityManager.clear();

        var loaded = service.get(object.getId());
        assertThat(loaded.getVersions())
                .extracting(v -> v.getS2Link())
                .containsExactly(LINK, LINK);
        assertThat(next.getS2Link()).isEqualTo(LINK);
    }

    @Test
    void api_setsClearsAndExposesLinkPublicly() throws Exception {
        var object = createObject();
        var versionPath = versionPath(object);

        mvc.perform(asOperationalEntity(put(versionPath + "/s2-link"))
                        .content("{\"s2Link\":\"" + LINK + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s2Link").value(LINK));

        service.publishVersion(object.getId(), object.getVersions().get(0).getId());
        when(currentUser.maySeeDrafts()).thenReturn(false);
        mvc.perform(get("/api/reference-data-objects/" + object.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions[0].s2Link").value(LINK));
    }

    @Test
    void api_clearsLinkWithNullOrEmptyBody() throws Exception {
        var object = createObject();
        var versionPath = versionPath(object);

        for (var body : List.of("{\"s2Link\":null}", "{}")) {
            service.updateVersionS2Link(object.getId(), object.getVersions().get(0).getId(),
                    new UpdateVersionS2LinkRequest().s2Link(LINK));
            mvc.perform(asOperationalEntity(put(versionPath + "/s2-link")).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.s2Link").isEmpty());
        }
    }

    @Test
    void api_rejectsNonHttpLinks() throws Exception {
        var versionPath = versionPath(createObject());

        for (var link : List.of("javascript:alert(1)", "ftp://s2.example.org/model", "", "https://")) {
            mvc.perform(asOperationalEntity(put(versionPath + "/s2-link"))
                            .content("{\"s2Link\":\"" + link + "\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void api_rejectsChangeOnPublishedVersion() throws Exception {
        var object = createObject();
        service.publishVersion(object.getId(), object.getVersions().get(0).getId());

        mvc.perform(asOperationalEntity(put(versionPath(object) + "/s2-link"))
                        .content("{\"s2Link\":\"" + LINK + "\"}"))
                .andExpect(status().isConflict());
    }

    private ReferenceDataObjectDetail createObject() {
        return service.create(new CreateReferenceDataObjectRequest()
                .name("Permission administrator")
                .description("description"));
    }

    private static String versionPath(ReferenceDataObjectDetail object) {
        return "/api/reference-data-objects/" + object.getId() + "/versions/" + object.getVersions().get(0).getId();
    }

    private static MockHttpServletRequestBuilder asOperationalEntity(MockHttpServletRequestBuilder request) {
        return request
                .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority())))
                .contentType(MediaType.APPLICATION_JSON);
    }
}
