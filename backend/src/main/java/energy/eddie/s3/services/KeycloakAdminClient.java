package energy.eddie.s3.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import energy.eddie.s3.exceptions.ConflictException;
import energy.eddie.s3.exceptions.NotFoundException;
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
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
public class KeycloakAdminClient {
    private static final int PAGE_SIZE = 100;
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper;
    private final String host;
    private final String realm;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;

    public KeycloakAdminClient(
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

    public JsonNode get(String path) {
        return find(path).orElseThrow(() -> new NotFoundException("Keycloak resource not found"));
    }

    public Optional<JsonNode> find(String path) {
        var response = send(authorized(path).GET().build());
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        return Optional.of(parse(response));
    }

    public List<JsonNode> getAll(String path) {
        var separator = path.contains("?") ? "&" : "?";
        List<JsonNode> result = new ArrayList<>();
        for (int first = 0; ; first += PAGE_SIZE) {
            var page = get(path + separator + "first=" + first + "&max=" + PAGE_SIZE);
            if (!page.isArray()) {
                throw unavailable();
            }
            page.forEach(result::add);
            if (page.size() < PAGE_SIZE) {
                return result;
            }
        }
    }

    public void put(String path, Object body) {
        require(send(authorized(path).header(CONTENT_TYPE, APPLICATION_JSON)
                .PUT(HttpRequest.BodyPublishers.ofString(json(body))).build()));
    }

    public void post(String path, Object body) {
        require(send(authorized(path).header(CONTENT_TYPE, APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json(body))).build()));
    }

    public String postForLocation(String path, Object body) {
        var response = send(authorized(path).header(CONTENT_TYPE, APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json(body))).build());
        require(response);
        return response.headers().firstValue("Location").orElseThrow(KeycloakAdminClient::unavailable);
    }

    public void delete(String path) {
        require(send(authorized(path).DELETE().build()));
    }

    private HttpRequest.Builder authorized(String path) {
        return request(host + "/admin/realms/" + realm + path).header("Authorization", "Bearer " + accessToken());
    }

    private String accessToken() {
        if (clientSecret.isBlank()) {
            log.warn("No client secret provided");
        }
        var form = "grant_type=client_credentials&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret);
        var request = request(tokenUri)
                .header(CONTENT_TYPE, "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        var token = parse(send(request)).path("access_token").asText();
        if (token.isBlank()) {
            log.warn("Token is blank");
        }
        return token;
    }

    private JsonNode parse(HttpResponse<String> response) {
        require(response);
        try {
            return mapper.readTree(response.body());
        } catch (IOException ex) {
            throw unavailable();
        }
    }

    private void require(HttpResponse<String> response) {
        if (response.statusCode() == 404) {
            throw new NotFoundException("Keycloak resource not found");
        }
        if (response.statusCode() == 409) {
            throw new ConflictException("Keycloak resource already exists");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw unavailable();
        }
    }

    private String json(Object body) {
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
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

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Keycloak directory unavailable");
    }
}
