package energy.eddie.s3.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keycloak")
public record KeycloakConfig(String host, String realm, String client, String authorizationUri, String tokenUri, String issuerUri, String jwkSetUri) {}

