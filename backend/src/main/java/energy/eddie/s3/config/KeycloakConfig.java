package energy.eddie.s3.config;

import javax.annotation.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keycloak")
public record KeycloakConfig(
        @Nullable String host,
        @Nullable String realm,
        @Nullable String client,
        @Nullable String authorizationUri,
        @Nullable String tokenUri,
        @Nullable String issuerUri,
        @Nullable String jwkSetUri) {}
