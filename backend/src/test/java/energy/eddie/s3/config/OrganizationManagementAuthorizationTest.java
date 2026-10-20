package energy.eddie.s3.config;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import energy.eddie.s3.controllers.OrganizationManagementController;
import energy.eddie.s3.generated.model.Nation;
import energy.eddie.s3.repositories.ResponsibilityRepository;
import energy.eddie.s3.security.CeedsRole;
import energy.eddie.s3.security.CurrentUser;
import energy.eddie.s3.security.OrganizationRolesConverter;
import energy.eddie.s3.services.KeycloakUserDirectory;
import energy.eddie.s3.services.OrganizationManagementService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrganizationManagementController.class)
@Import({SecurityConfig.class, CorsConfig.class, OrganizationRolesConverter.class, CurrentUser.class,
        OrganizationManagementService.class})
class OrganizationManagementAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockitoBean KeycloakUserDirectory directory;
    @MockitoBean ResponsibilityRepository responsibilities;
    @MockitoBean JwtDecoder decoder;

    private final UUID organizationId = UUID.randomUUID();

    @ParameterizedTest
    @ValueSource(strings = {"\"role\":\"NDSF\"", "\"roles\":[\"NDSF\"]"})
    void singleRoleAndRolesArrayRequestsRemainAccepted(String roleProperty) throws Exception {
        var organization = new KeycloakUserDirectory.KeycloakOrganization(
                organizationId, "agency", "Agency", List.of(CeedsRole.NDSF), List.of(Nation.AUT));
        when(directory.createOrganization("Agency", "agency", List.of(CeedsRole.NDSF), List.of(Nation.AUT)))
                .thenReturn(organization);
        when(directory.findOrganization(organizationId)).thenReturn(Optional.of(organization));
        var body = "{\"name\":\"Agency\",\"nations\":[\"AUT\"]," + roleProperty + "}";

        mvc.perform(post("/api/management/organizations")
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority())))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("NDSF"))
                .andExpect(jsonPath("$.role").value("NDSF"));
        mvc.perform(put("/api/management/organizations/{id}", organizationId)
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority())))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("NDSF"));
        verify(responsibilities, never()).deleteByOrganizationId(organizationId);
    }

    @Test
    void mixedRoleUpdateRetainsBothRolesAndNations() throws Exception {
        var roles = List.of(CeedsRole.NDSF, CeedsRole.OPERATIONAL_ENTITY);
        when(directory.findOrganization(organizationId)).thenReturn(Optional.of(
                new KeycloakUserDirectory.KeycloakOrganization(organizationId, "mixed", "Mixed", roles, List.of(Nation.AUT))));

        mvc.perform(put("/api/management/organizations/{id}", organizationId)
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"NDSF\",\"OPERATIONAL_ENTITY\"],\"nations\":[\"AUT\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("NDSF", "OPERATIONAL_ENTITY")))
                .andExpect(jsonPath("$.nations[0]").value("AUT"));
        verify(directory).updateOrganization(organizationId, roles, List.of(Nation.AUT));
        verify(responsibilities, never()).deleteByOrganizationId(organizationId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"roles\":[]", ",\"roles\":[null]"})
    void missingEmptyOrNullRolesAreBadRequests(String rolesProperty) throws Exception {
        mvc.perform(post("/api/management/organizations")
                        .with(jwt().authorities(new SimpleGrantedAuthority(CeedsRole.OPERATIONAL_ENTITY.authority())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Agency\"" + rolesProperty + "}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(directory, responsibilities);
    }
}
