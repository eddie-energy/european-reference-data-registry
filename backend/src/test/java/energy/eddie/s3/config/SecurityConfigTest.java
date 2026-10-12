package energy.eddie.s3.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import energy.eddie.s3.controllers.ReferenceDataEntryController;
import energy.eddie.s3.controllers.ReferenceDataObjectController;
import energy.eddie.s3.controllers.ResponsibilitiesController;
import energy.eddie.s3.controllers.UiController;
import energy.eddie.s3.controllers.OrganizationManagementController;
import energy.eddie.s3.controllers.UserManagementController;
import energy.eddie.s3.services.OrganizationManagementService;
import energy.eddie.s3.generated.model.ManagementUserDto;
import energy.eddie.s3.generated.model.ManagedOrganizationDto;
import energy.eddie.s3.services.UserManagementService;
import energy.eddie.s3.services.ReferenceDataEntryService;
import energy.eddie.s3.generated.model.ReferenceDataEntryDto;
import energy.eddie.s3.generated.model.FieldDto;
import energy.eddie.s3.generated.model.ReferenceDataObjectDetail;
import energy.eddie.s3.security.CeedsRole;
import energy.eddie.s3.security.OrganizationRolesConverter;
import energy.eddie.s3.services.ReferenceDataObjectService;
import energy.eddie.s3.services.ResponsibilityManagementService;
import energy.eddie.s3.services.KeycloakOrganizationDirectory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {ReferenceDataObjectController.class, ReferenceDataEntryController.class, ResponsibilitiesController.class, UserManagementController.class, OrganizationManagementController.class, UiController.class})
@Import({SecurityConfig.class, CorsConfig.class, OrganizationRolesConverter.class})
class SecurityConfigTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String CREATE_USER_BODY =
            "{\"username\":\"new.user\",\"temporaryPassword\":\"s3cret-pass\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReferenceDataObjectService referenceDataObjectService;

    @MockitoBean
    private ReferenceDataEntryService referenceDataEntryService;

    @MockitoBean
    private ResponsibilityManagementService responsibilityManagementService;

    @MockitoBean
    private KeycloakOrganizationDirectory keycloakOrganizationDirectory;

    @MockitoBean
    private UserManagementService userManagementService;

    @MockitoBean
    private OrganizationManagementService organizationManagementService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void readWithoutToken_isAllowed() throws Exception {
        given(referenceDataObjectService.getAll()).willReturn(List.of());

        mockMvc.perform(get("/api/reference-data-objects")).andExpect(status().isOk());
    }

    @Test
    void writeWithoutToken_isRejectedWithErrorResponseBody() throws Exception {
        mockMvc.perform(post("/api/reference-data-objects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tariffs\",\"description\":\"desc\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void writeWithoutTheRequiredRole_isForbiddenWithErrorResponseBody() throws Exception {
        mockMvc.perform(post("/api/reference-data-objects")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tariffs\",\"description\":\"desc\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void writeAsOperationalEntity_isAllowed() throws Exception {
        given(referenceDataObjectService.create(any())).willReturn(new ReferenceDataObjectDetail());

        mockMvc.perform(post("/api/reference-data-objects")
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tariffs\",\"description\":\"desc\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void categoryUpdate_requiresOperationalEntity() throws Exception {
        given(referenceDataObjectService.updateCategory(any(), any())).willReturn(new ReferenceDataObjectDetail());

        mockMvc.perform(put("/api/reference-data-objects/{id}/category", ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.NDSF.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"ROLE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/reference-data-objects/{id}/category", ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"ROLE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void referenceDataEntryWriteAsNdsf_isAllowed() throws Exception {
        given(referenceDataEntryService.createReferenceDataEntry(any(), any(), any()))
                .willReturn(new ReferenceDataEntryDto());

        mockMvc.perform(post(
                                "/api/reference-data-objects/{id}/versions/{versionId}/reference-data-entries",
                                ID,
                                ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.NDSF.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nation\":\"AUT\",\"values\":[]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void referenceDataEntryReadWithoutToken_isAllowed() throws Exception {
        given(referenceDataEntryService.listReferenceDataEntries(any(), any())).willReturn(List.of());

        mockMvc.perform(get(
                        "/api/reference-data-objects/{id}/versions/{versionId}/reference-data-entries",
                        ID,
                        ID))
                .andExpect(status().isOk());
    }

    @Test
    void legacyEntryRoute_isNotFound() throws Exception {
        mockMvc.perform(post("/api/reference-data-objects/{id}/versions/{versionId}/entries", ID, ID)
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nation\":\"AUT\",\"values\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void fieldWriteAsNdsf_isAllowed() throws Exception {
        given(referenceDataObjectService.createField(any(), any(), any())).willReturn(new FieldDto());

        mockMvc.perform(post("/api/reference-data-objects/{id}/versions/{versionId}/fields", ID, ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.NDSF.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"austria_grid_id\",\"dataType\":\"TEXT\",\"mandatory\":false,\"nation\":\"AUT\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void versionCreateAsNdsf_isForbidden() throws Exception {
        mockMvc.perform(post("/api/reference-data-objects/{id}/versions", ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.NDSF.authority()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void fieldDeleteAsNdsf_isForbidden() throws Exception {
        mockMvc.perform(delete(
                                "/api/reference-data-objects/{id}/versions/{versionId}/fields/{fieldId}", ID, ID, ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.NDSF.authority()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void objectWriteAsNdsf_isForbidden() throws Exception {
        mockMvc.perform(post("/api/reference-data-objects")
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.NDSF.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tariffs\",\"description\":\"desc\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void responsibilityReadAsNdsf_isForbidden() throws Exception {
        mockMvc.perform(get("/api/reference-data-objects/{id}/responsibilities", ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.NDSF.authority()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void responsibilityReadAsOperationalEntity_isAllowed() throws Exception {
        mockMvc.perform(get("/api/reference-data-objects/{id}/responsibilities", ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority()))))
                .andExpect(status().isOk());
    }

    @Test
    void userListAsNdsf_isForbidden() throws Exception {
        mockMvc.perform(get("/api/management/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.NDSF.authority()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void userListAsOperationalEntity_isAllowed() throws Exception {
        mockMvc.perform(get("/api/management/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority()))))
                .andExpect(status().isOk());
    }

    @Test
    void userCreateAsNdsf_isForbidden() throws Exception {
        mockMvc.perform(post("/api/management/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.NDSF.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_USER_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCreateAsOperationalEntity_isCreated() throws Exception {
        given(userManagementService.create(any())).willReturn(new ManagementUserDto());

        mockMvc.perform(post("/api/management/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_USER_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void userCreateWithoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/management/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_USER_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void organizationCreateAsNdsf_isForbidden() throws Exception {
        mockMvc.perform(post("/api/management/organizations")
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.NDSF.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Agency\",\"role\":\"NDSF\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizationCreateAsOperationalEntity_isCreated() throws Exception {
        given(organizationManagementService.create(any())).willReturn(new ManagedOrganizationDto());

        mockMvc.perform(post("/api/management/organizations")
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Agency\",\"role\":\"NDSF\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void organizationUpdateAsNdsf_isForbidden() throws Exception {
        mockMvc.perform(put("/api/management/organizations/{id}", ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.NDSF.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"NDSF\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizationUpdateAsOperationalEntity_isAllowed() throws Exception {
        given(organizationManagementService.update(any(), any())).willReturn(new ManagedOrganizationDto());

        mockMvc.perform(put("/api/management/organizations/{id}", ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"NDSF\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void organizationListAsNdsf_isForbidden() throws Exception {
        mockMvc.perform(get("/api/management/all-organizations")
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.NDSF.authority()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void userListWithoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/management/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void organizationDirectoryWithoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/management/organizations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void spaShell_isPublic() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void spaDeepLink_isPublic() throws Exception {
        mockMvc.perform(get("/reference-data-objects/create")).andExpect(status().isOk());
    }

    @Test
    void openApiContract_isPublic() throws Exception {
        mockMvc.perform(get("/backend-api.yml")).andExpect(status().isOk());
    }
}
