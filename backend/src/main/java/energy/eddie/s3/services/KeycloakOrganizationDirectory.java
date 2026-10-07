package energy.eddie.s3.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import energy.eddie.s3.generated.model.EligibleOrganizationDto;
import energy.eddie.s3.generated.model.Nation;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
public class KeycloakOrganizationDirectory {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper;
    private final String host;
    private final String realm;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;

    public KeycloakOrganizationDirectory(
            ObjectMapper mapper,
            @Value("${keycloak.host}") String host,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.token-uri}") String tokenUri,
            @Value("${keycloak.directory-client-id:ceeds-directory}") String clientId,
            @Value("${keycloak.directory-client-secret:}") String clientSecret) {
        this.mapper = mapper;
        this.host = host;
        this.realm = realm;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public List<EligibleOrganizationDto> list() {
        var token = accessToken();
        List<EligibleOrganizationDto> result = new ArrayList<>();
        for (int first = 0; ; first += 100) {
            var page = get(host + "/admin/realms/" + realm
                    + "/organizations?briefRepresentation=false&first=" + first + "&max=100", token);
            if (!page.isArray()) {
                throw unavailable();
            }
            page.forEach(node -> {
                if (eligible(node)) {
                    result.add(toDto(node));
                }
            });
            if (page.size() < 100) {
                return result;
            }
        }
    }

    public boolean isEligible(UUID organizationId) {
        var token = accessToken();
        var request = request(host + "/admin/realms/" + realm + "/organizations/" + organizationId)
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        var response = send(request);
        if (response.statusCode() == 404) {
            return false;
        }
        return eligible(body(response));
    }

    private String accessToken() {
        if (clientSecret.isBlank()) {
            log.warn("No client secret provided");
        }
        var form = "grant_type=client_credentials&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret);
        var request = request(tokenUri)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        var token = body(send(request)).path("access_token").asText();
        if (token.isBlank()) {
            log.warn("Token is blank");
        }
        return token;
    }

    private JsonNode get(String uri, String token) {
        return body(send(request(uri).header("Authorization", "Bearer " + token).GET().build()));
    }

    private JsonNode body(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw unavailable();
        }
        try {
            return mapper.readTree(response.body());
        } catch (IOException ex) {
            throw unavailable();
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw unavailable();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private static HttpRequest.Builder request(String uri) {
        return HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(5));
    }

    private static boolean eligible(JsonNode node) {
        return node.path("enabled").asBoolean(true) && values(node.path("attributes").path("ceeds_role"))
                .contains("NDSF");
    }

    private static EligibleOrganizationDto toDto(JsonNode node) {
        var nations = values(node.path("attributes").path("ceeds_nations")).stream()
                .map(value -> {
                    try {
                        return Nation.fromValue(value);
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return new EligibleOrganizationDto()
                .id(UUID.fromString(node.path("id").asText()))
                .alias(node.path("alias").asText())
                .name(node.path("name").asText())
                .keycloakNations(nations);
    }

    private static List<String> values(JsonNode node) {
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(item -> values.add(item.asText()));
            return values;
        }
        return node.isTextual() ? List.of(node.asText()) : List.of();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Keycloak organization directory unavailable");
    }
}
