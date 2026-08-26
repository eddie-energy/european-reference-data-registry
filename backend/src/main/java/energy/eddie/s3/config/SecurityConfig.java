package energy.eddie.s3.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import energy.eddie.s3.generated.model.ErrorResponse;
import energy.eddie.s3.security.CeedsRole;
import energy.eddie.s3.security.OrganizationRolesConverter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] REFERENCE_DATA_PATHS = {
        "/api/reference-data-objects", "/api/reference-data-objects/**"
    };

    private static final String[] ENTRY_PATHS = {
        "/api/reference-data-objects/*/versions/*/entries",
        "/api/reference-data-objects/*/versions/*/entries/*",
        "/api/reference-data-objects/*/entries/*"
    };

    private static final String[] NDSF_WRITABLE_PATHS = {
        "/api/reference-data-objects/*/versions",
        "/api/reference-data-objects/*/versions/*/fields"
    };

    private static final String[] PUBLIC_PATHS = {
        "/",
        "/index.html",
        "/favicon.svg",
        "/INSIEME_Logo.png",
        "/assets/**",
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/backend-api.yml",
        "/actuator/health",
        "/actuator/health/**",
        "/{a:[^.]*}",
        "/{a:[^.]*}/{b:[^.]*}"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            OrganizationRolesConverter organizationRolesConverter)
            throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET, REFERENCE_DATA_PATHS)
                        .permitAll()
                        .requestMatchers(ENTRY_PATHS)
                        .hasAnyRole(CeedsRole.NDSF.name(), CeedsRole.OPERATIONAL_ENTITY.name())
                        .requestMatchers(HttpMethod.POST, NDSF_WRITABLE_PATHS)
                        .hasAnyRole(CeedsRole.NDSF.name(), CeedsRole.OPERATIONAL_ENTITY.name())
                        .requestMatchers(REFERENCE_DATA_PATHS)
                        .hasRole(CeedsRole.OPERATIONAL_ENTITY.name())
                        .requestMatchers("/api/**")
                        .authenticated()
                        .requestMatchers(PUBLIC_PATHS)
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                                jwt -> jwt.jwtAuthenticationConverter(organizationRolesConverter))
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> writeError(
                response, objectMapper, HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) ->
                writeError(response, objectMapper, HttpStatus.FORBIDDEN, "Your role does not permit this operation");
    }

    private static void writeError(
            HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String message)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ErrorResponse().status(status.value()).message(message));
    }
}
