package com.company.project.cleaning;

import com.company.project.Application;
import com.company.project.domain.User;
import com.company.project.security.JwtTokenProvider;
import com.company.project.service.CleaningLobbyService;
import com.company.project.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"cleaning.plc.enabled=false", "cleaning.realtime.push-interval-ms=100",
                "spring.profiles.active=offline-test","spring.datasource.url=jdbc:mysql://127.0.0.1:9/offline_test",
                "spring.datasource.username=offline","spring.datasource.password=offline",
                "jwt.secret=offline-unit-tests-secret-at-least-32-bytes","jwt.expiration=86400000"})
class RealtimePushIntegrationTest {
    @LocalServerPort int port;
    @Autowired JwtTokenProvider tokens;
    @Autowired CleaningLobbyService lobby;
    @Autowired ObjectMapper json;
    @MockBean UserService users;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach void account() {
        enabled.set(true);
        when(users.getByUsername("alice")).thenAnswer(invocation -> {
            var user = new User();
            user.setUsername("alice"); user.setStatus(enabled.get() ? 1 : 0); user.setRoleCode("VIEWER");
            return user;
        });
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(5));
    }

    private String nextEvent(BufferedReader reader) throws Exception {
        var read = CompletableFuture.supplyAsync(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) if (line.startsWith("data:")) return line.substring(5).trim();
                return null;
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        });
        return read.get(5, TimeUnit.SECONDS);
    }

    @Test void authenticatedStreamReceivesFactsAndStopsAfterAccountIsDisabled() throws Exception {
        String token = tokens.generateToken("alice", "VIEWER");
        var response = client.send(request("/api/cleaning/devices/plc-001/realtime/stream")
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/event-stream"));
        try (var body = response.body(); var reader = new BufferedReader(new InputStreamReader(body))) {
            JsonNode initial = json.readTree(nextEvent(reader));
            assertFalse(initial.path("data").path("available").asBoolean());
            lobby.acceptDeviceSnapshot(BusinessOwnershipTest.snapshot(1, "321", true));
            JsonNode update = json.readTree(nextEvent(reader));
            assertTrue(update.path("data").path("available").asBoolean());
            assertEquals(321, update.path("data").path("data").path("ropes").get(0).path("tension").asInt());
            var statistics = client.send(request("/api/cleaning/devices/plc-001/statistics")
                    .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, statistics.statusCode());
            assertEquals(1, json.readTree(statistics.body()).path("data").path("acceptedSamples").asLong());
            enabled.set(false);
            var denied=client.send(request("/api/cleaning/devices/plc-001/realtime")
                    .header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.discarding());
            assertEquals(403,denied.statusCode());
            assertNull(nextEvent(reader), "disabled accounts must not continue receiving measurements");
        }
    }

    @Test void anonymousStreamIsRejectedBeforeOpeningConnection() throws Exception {
        var response = client.send(request("/api/cleaning/devices/plc-001/realtime/stream").GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(401, response.statusCode());
    }
}
