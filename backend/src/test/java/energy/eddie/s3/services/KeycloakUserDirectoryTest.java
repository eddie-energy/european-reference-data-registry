package energy.eddie.s3.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import energy.eddie.s3.generated.model.Nation;
import energy.eddie.s3.security.CeedsRole;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class KeycloakUserDirectoryTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void listsUsersWithCountryAttribute() throws IOException {
        var id = UUID.randomUUID();
        var directory = directory(http -> http.createContext("/admin/realms/ceeds/users", exchange -> respond(
                exchange, 200,
                "[{\"id\":\"" + id + "\",\"username\":\"ceeds\",\"attributes\":{\"ceeds_country\":[\"AUT\"]}},"
                        + "{\"id\":\"" + UUID.randomUUID() + "\",\"username\":\"other\"}]")));

        var users = directory.listUsers();

        assertThat(users).extracting(KeycloakUserDirectory.KeycloakUser::username).containsExactly("ceeds", "other");
        assertThat(users.get(0).country()).isEqualTo(Nation.AUT);
        assertThat(users.get(1).country()).isNull();
    }

    @Test
    void readsUserOrganizationsFromMembersEndpoint() throws IOException {
        var userId = UUID.randomUUID();
        var organizationId = UUID.randomUUID();
        var directory = directory(http -> http.createContext(
                "/admin/realms/ceeds/organizations/members/" + userId + "/organizations",
                exchange -> respond(exchange, 200, "[{\"id\":\"" + organizationId + "\",\"alias\":\"eda\"}]")));

        assertThat(directory.userOrganizationIds(userId)).containsExactly(organizationId);
    }

    @Test
    void listsOrganizationDetailsWithRoleAndNations() throws IOException {
        var id = UUID.randomUUID();
        var directory = directory(http -> http.createContext("/admin/realms/ceeds/organizations", exchange -> respond(
                exchange, 200,
                "[{\"id\":\"" + id + "\",\"alias\":\"eda\",\"name\":\"EDA\",\"enabled\":true,"
                        + "\"attributes\":{\"ceeds_role\":[\"NDSF\"],\"ceeds_nations\":[\"AUT\",\"GER\"]}},"
                        + "{\"id\":\"" + UUID.randomUUID() + "\",\"alias\":\"off\",\"name\":\"Off\",\"enabled\":false}]")));

        assertThat(directory.listOrganizationDetails()).singleElement().satisfies(organization -> {
            assertThat(organization.role()).isEqualTo(CeedsRole.NDSF);
            assertThat(organization.nations()).containsExactly(Nation.AUT, Nation.GER);
        });
    }

    @Test
    void updateOrganizationWritesRoleAndNationsAndKeepsOtherFields() throws IOException {
        var id = UUID.randomUUID();
        var written = new AtomicReference<String>();
        var directory = directory(http -> http.createContext("/admin/realms/ceeds/organizations/" + id, exchange -> {
            if ("PUT".equals(exchange.getRequestMethod())) {
                written.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                respond(exchange, 204, "");
            } else {
                respond(exchange, 200, "{\"id\":\"" + id + "\",\"alias\":\"eda\",\"name\":\"EDA\",\"enabled\":true}");
            }
        }));

        directory.updateOrganization(id, CeedsRole.NDSF, List.of(Nation.FRA, Nation.ESP));

        assertThat(written.get())
                .contains("\"alias\":\"eda\"")
                .contains("\"ceeds_role\":[\"NDSF\"]")
                .contains("\"ceeds_nations\":[\"FRA\",\"ESP\"]");
    }

    @Test
    void createUserSendsTemporaryPasswordAndReturnsIdFromLocation() throws IOException {
        var id = UUID.randomUUID();
        var written = new AtomicReference<String>();
        var directory = directory(http -> http.createContext("/admin/realms/ceeds/users", exchange -> {
            written.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Location", "http://keycloak/admin/realms/ceeds/users/" + id);
            respond(exchange, 201, "");
        }));

        assertThat(directory.createUser("new.user", "s3cret-pass", Nation.FRA)).isEqualTo(id);
        assertThat(written.get())
                .contains("\"username\":\"new.user\"")
                .contains("\"temporary\":true")
                .contains("\"ceeds_country\":[\"FRA\"]");
    }

    @Test
    void createOrganizationSendsRoleAttributesAndReturnsIdFromLocation() throws IOException {
        var id = UUID.randomUUID();
        var written = new AtomicReference<String>();
        var directory = directory(http -> http.createContext("/admin/realms/ceeds/organizations", exchange -> {
            written.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Location", "http://keycloak/admin/realms/ceeds/organizations/" + id);
            respond(exchange, 201, "");
        }));

        var organization = directory.createOrganization("Agency", "agency", CeedsRole.NDSF, List.of(Nation.AUT));

        assertThat(organization.id()).isEqualTo(id);
        assertThat(written.get())
                .contains("\"alias\":\"agency\"")
                .contains("\"ceeds_role\":[\"NDSF\"]")
                .contains("\"ceeds_nations\":[\"AUT\"]");
    }

    @Test
    void createUserWithTakenUsernameIsConflict() throws IOException {
        var directory = directory(http -> http.createContext(
                "/admin/realms/ceeds/users", exchange -> respond(exchange, 409, "{}")));

        assertThatThrownBy(() -> directory.createUser("taken", "s3cret-pass", null))
                .isInstanceOf(energy.eddie.s3.exceptions.ConflictException.class);
    }

    @Test
    void updateCountryWritesAttributeAndKeepsOtherFields() throws IOException {
        var id = UUID.randomUUID();
        var written = new AtomicReference<String>();
        var directory = directory(http -> http.createContext("/admin/realms/ceeds/users/" + id, exchange -> {
            if ("PUT".equals(exchange.getRequestMethod())) {
                written.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                respond(exchange, 204, "");
            } else {
                respond(exchange, 200, "{\"id\":\"" + id + "\",\"username\":\"ceeds\",\"enabled\":true}");
            }
        }));

        directory.updateCountry(id, Nation.GER);

        assertThat(written.get()).contains("\"username\":\"ceeds\"").contains("\"ceeds_country\":[\"GER\"]");
    }

    private KeycloakUserDirectory directory(java.util.function.Consumer<HttpServer> routes) throws IOException {
        server = HttpServer.create(new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/token", exchange -> respond(exchange, 200, "{\"access_token\":\"test\"}"));
        routes.accept(server);
        server.start();
        var base = "http://127.0.0.1:" + server.getAddress().getPort();
        var mapper = new ObjectMapper();
        return new KeycloakUserDirectory(
                new KeycloakAdminClient(mapper, base, "ceeds", base + "/token", "ceeds-directory", "secret"), mapper);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, status == 204 || status == 201 ? -1 : bytes.length);
        if (status != 204 && status != 201) {
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
        exchange.close();
    }
}
