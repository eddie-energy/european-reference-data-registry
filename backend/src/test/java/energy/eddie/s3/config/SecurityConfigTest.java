package energy.eddie.s3.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import energy.eddie.s3.controllers.EntryController;
import energy.eddie.s3.controllers.ReferenceDataObjectController;
import energy.eddie.s3.controllers.UiController;
import energy.eddie.s3.services.EntryService;
import energy.eddie.s3.services.ReferenceDataObjectService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {ReferenceDataObjectController.class, EntryController.class, UiController.class})
@Import({SecurityConfig.class, CorsConfig.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReferenceDataObjectService referenceDataObjectService;

    @MockitoBean
    private EntryService entryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void apiRequestWithoutToken_isRejectedWithErrorResponseBody() throws Exception {
        mockMvc.perform(get("/api/reference-data-objects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void apiRequestWithToken_isAllowed() throws Exception {
        given(referenceDataObjectService.getAll()).willReturn(List.of());

        mockMvc.perform(get("/api/reference-data-objects").with(jwt()))
                .andExpect(status().isOk());
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
