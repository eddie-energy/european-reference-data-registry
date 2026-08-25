package energy.eddie.s3.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import energy.eddie.s3.generated.model.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
        // SPA deep links served by UiController: any path segment without a dot.
        "/{a:[^.]*}",
        "/{a:[^.]*}/{b:[^.]*}"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            AuthenticationEntryPoint authenticationEntryPoint)
            throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Order matters: the SPA deep-link patterns below also match /api/... paths,
                // so the API rule has to be evaluated first.
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/**")
                        .authenticated()
                        .requestMatchers(PUBLIC_PATHS)
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
                .build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> writeError(
                response, objectMapper, HttpStatus.UNAUTHORIZED, "Authentication required");
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
