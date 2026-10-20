package energy.eddie.s3.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class KeycloakOrganizationDirectoryTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"NDSF", "COORDINATING_NDSF"})
    void listsOnlyNdsfOrganizationsWithKeycloakNations(String role) throws IOException {
        var id = UUID.randomUUID();
        server = HttpServer.create(new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/token", exchange -> respond(exchange, 200, "{\"access_token\":\"test\"}"));
        server.createContext("/admin/realms/ceeds/organizations", exchange -> respond(
                exchange,
                200,
                "[{\"id\":\"" + id + "\",\"alias\":\"eda\",\"name\":\"EDA\",\"enabled\":true,"
                        + "\"attributes\":{\"ceeds_role\":[\"" + role + "\"],\"ceeds_nations\":[\"GER\"]}},"
                        + "{\"id\":\"" + UUID.randomUUID() + "\",\"alias\":\"op\",\"name\":\"OP\","
                        + "\"attributes\":{\"ceeds_role\":[\"OPERATIONAL_ENTITY\"]}}]"));
        server.createContext("/admin/realms/ceeds/organizations/" + id, exchange -> respond(
                exchange,
                200,
                "{\"id\":\"" + id + "\",\"alias\":\"eda\",\"name\":\"EDA\",\"enabled\":true,"
                        + "\"attributes\":{\"ceeds_role\":[\"" + role + "\"],\"ceeds_nations\":[\"GER\"]}}"));
        server.start();
        var base = "http://127.0.0.1:" + server.getAddress().getPort();
        var directory = new KeycloakOrganizationDirectory(new KeycloakAdminClient(
                new ObjectMapper(), base, "ceeds", base + "/token", "ceeds-directory", "secret"));

        assertThat(directory.list()).singleElement().satisfies(organization -> {
            assertThat(organization.getId()).isEqualTo(id);
            assertThat(organization.getKeycloakNations()).containsExactly(energy.eddie.s3.generated.model.Nation.GER);
        });
        assertThat(directory.isEligible(id)).isTrue();
    }

    @Test
    void missingCredentialReturnsServiceUnavailable() throws IOException {
        var requests = new AtomicInteger();
        server = HttpServer.create(
                new InetSocketAddress(java.net.InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 0);
        server.createContext("/token", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 404, "{}");
        });
        server.start();
        var base = "http://127.0.0.1:" + server.getAddress().getPort();
        var directory = new KeycloakOrganizationDirectory(new KeycloakAdminClient(
                new ObjectMapper(), base, "ceeds", base + "/token", "ceeds-directory", ""));

        assertThatThrownBy(directory::list)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(requests.get()).isZero();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
