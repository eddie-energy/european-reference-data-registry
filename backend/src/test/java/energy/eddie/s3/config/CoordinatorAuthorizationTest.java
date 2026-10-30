package energy.eddie.s3.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import energy.eddie.s3.controllers.MeController;
import energy.eddie.s3.controllers.ResponsibilitiesController;
import energy.eddie.s3.models.referencedata.Nation;
import energy.eddie.s3.models.referencedata.ReferenceDataObject;
import energy.eddie.s3.models.referencedata.Responsibility;
import energy.eddie.s3.repositories.ReferenceDataObjectRepository;
import energy.eddie.s3.repositories.ResponsibilityRepository;
import energy.eddie.s3.security.CurrentUser;
import energy.eddie.s3.security.OrganizationRolesConverter;
import energy.eddie.s3.services.KeycloakOrganizationDirectory;
import energy.eddie.s3.services.ResponsibilityManagementService;
import energy.eddie.s3.services.ResponsibilityService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {MeController.class, ResponsibilitiesController.class})
@Import({SecurityConfig.class, CorsConfig.class, OrganizationRolesConverter.class, CurrentUser.class,
        ResponsibilityManagementService.class, ResponsibilityService.class})
class CoordinatorAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean ResponsibilityRepository responsibilities;
    @MockitoBean ReferenceDataObjectRepository objects;
    @MockitoBean KeycloakOrganizationDirectory directory;

    private final UUID objectId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final UUID ordinaryOrganizationId = UUID.randomUUID();

    @BeforeEach
    void authenticateMixedMemberships() {
        when(decoder.decode("coordinator")).thenReturn(Jwt.withTokenValue("coordinator")
                .header("alg", "none").subject("user")
                .claim("organization", Map.of(
                        "coordinator", Map.of("id", organizationId.toString(),
                                "ceeds_role", "COORDINATING_NDSF", "ceeds_nations", "AUT"),
                        "ordinary", Map.of("id", ordinaryOrganizationId.toString(),
                                "ceeds_role", "NDSF", "ceeds_nations", "DEU")))
                .build());
    }

    @Test
    void missingNationIsRejectedBeforeServiceAccess() throws Exception {
        mvc.perform(post("/api/reference-data-objects/{id}/responsibilities", objectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer coordinator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + organizationId + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(responsibilities, objects, directory);
    }

    @Test
    void currentUserExposesOnlyCoordinatingMembershipNationsAsCoordinatingScope() throws Exception {
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer coordinator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coordinatingNdsfNations.length()").value(1))
                .andExpect(jsonPath("$.coordinatingNdsfNations[0]").value("AUT"))
                .andExpect(jsonPath("$.ndsfNations.length()").value(2))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasItems("NDSF", "COORDINATING_NDSF")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void coordinatorCanAssignItsNationToOwnOrOtherOrganization(boolean ownOrganization) throws Exception {
        var targetId = ownOrganization ? organizationId : ordinaryOrganizationId;
        var object = new ReferenceDataObject("Permissions", "description");
        ReflectionTestUtils.setField(object, "id", objectId);
        when(objects.findById(objectId)).thenReturn(Optional.of(object));
        when(directory.isEligible(targetId)).thenReturn(true);
        when(responsibilities.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        mvc.perform(post("/api/reference-data-objects/{id}/responsibilities", objectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer coordinator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + targetId + "\",\"nation\":\"AUT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value(targetId.toString()))
                .andExpect(jsonPath("$.nation").value("AUT"));
    }

    @Test
    void coordinatorCanRemoveVisibleResponsibilityOfAnotherOrganization() throws Exception {
        when(objects.existsById(objectId)).thenReturn(true);
        when(responsibilities.existsByReferenceDataObjectIdAndOrganizationIdAndNation(
                objectId, ordinaryOrganizationId, Nation.AUT)).thenReturn(true);

        mvc.perform(delete("/api/reference-data-objects/{id}/responsibilities/{org}/AUT", objectId, ordinaryOrganizationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer coordinator"))
                .andExpect(status().isNoContent());
        verify(responsibilities).deleteByReferenceDataObjectIdAndOrganizationIdAndNation(
                objectId, ordinaryOrganizationId, Nation.AUT);
    }

    @Test
    void ordinaryMembershipDoesNotPermitAssigningOrRemovingItsNation() throws Exception {
        mvc.perform(post("/api/reference-data-objects/{id}/responsibilities", objectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer coordinator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + organizationId + "\",\"nation\":\"DEU\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/reference-data-objects/{id}/responsibilities/{org}/DEU", objectId, organizationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer coordinator"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(responsibilities, objects, directory);
    }

    @Test
    void responsibilityListFiltersForeignNationsThroughAuthenticatedApi() throws Exception {
        var object = new ReferenceDataObject("Permissions", "description");
        ReflectionTestUtils.setField(object, "id", objectId);
        when(objects.existsById(objectId)).thenReturn(true);
        when(responsibilities.findByReferenceDataObjectId(objectId)).thenReturn(List.of(
                new Responsibility(object, organizationId, Nation.AUT),
                new Responsibility(object, organizationId, Nation.DEU)));

        mvc.perform(get("/api/reference-data-objects/{id}/responsibilities", objectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer coordinator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nation").value("AUT"));
    }
}
