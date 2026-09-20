package com.devlensai.backend.ai;

import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.exception.AiProviderApiException;
import com.devlensai.backend.exception.AiProviderMalformedResponseException;
import com.devlensai.backend.exception.AiProviderTimeoutException;
import com.devlensai.backend.exception.AiProviderUnavailableException;
import com.devlensai.backend.exception.OllamaSelectionException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaAiProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<HttpServer> servers = new ArrayList<>();
    private static final String RESULT = """
            {"summary":"Safe review","potentialBugs":[],"timeComplexity":"O(1)",
            "spaceComplexity":"O(1)","edgeCases":[],"suggestions":[],"improvedCode":"class Main {}",
            "generatedTestCases":[],"securityFindings":[]}
            """;

    @AfterEach void stop() { servers.forEach(server -> server.stop(0)); }

    @Test void listsActualModelsAndReviewsWithSelectedProfile() throws Exception {
        HttpServer server = server();
        server.createContext("/api/tags", exchange -> respond(exchange, 200,
                "{\"models\":[{\"name\":\"installed:latest\"}]}"));
        server.createContext("/api/chat", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(request).contains("installed:latest", "num_predict", "num_ctx", "class Main {}")
                    .doesNotContain("api_key");
            respond(exchange, 200, mapper.writeValueAsString(Map.of("message", Map.of("content", RESULT))));
        });
        server.start();
        OllamaAiProvider provider = provider(server, Duration.ofSeconds(2));
        assertThat(provider.installedModels("local")).containsExactly("installed:latest");
        assertThat(provider.review(ProgrammingLanguage.JAVA, "class Main {}", "local", "installed:latest")
                .summary()).isEqualTo("Safe review");
    }

    @Test void rejectsMissingOrUnsafeConfiguration() {
        assertThatThrownBy(() -> new OllamaConnections(""))
                .isInstanceOf(IllegalArgumentException.class);
        for (String url : List.of("http://169.254.169.254:11434", "https://example.com:11434",
                "http://10.0.0.2:11434/path", "http://127.0.0.1:11434@evil.test:11434")) {
            assertThatThrownBy(() -> new OllamaConnections("local|Local|" + url))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new OllamaAiProvider(mapper,
                new OllamaConnections("local|Local|http://localhost:11434"), "local", "",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 2048, 8192))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void reportsModelMissingWithoutPostingSource() throws Exception {
        HttpServer server = server();
        server.createContext("/api/tags", exchange -> respond(exchange, 200,
                "{\"models\":[{\"name\":\"other:latest\"}]}"));
        server.start();
        assertThatThrownBy(() -> provider(server, Duration.ofSeconds(2)).review(
                ProgrammingLanguage.JAVA, "PRIVATE_SOURCE", "local", "missing:latest"))
                .isInstanceOf(OllamaSelectionException.class)
                .hasMessageContaining("no longer installed")
                .hasMessageNotContaining("PRIVATE_SOURCE");
    }

    @Test void handlesServiceUnavailableAndRedirectWithoutFollowing() throws Exception {
        HttpServer server = server();
        server.createContext("/api/tags", exchange -> respond(exchange, 503, "secret response"));
        server.start();
        assertThatThrownBy(() -> provider(server, Duration.ofSeconds(2)).installedModels("local"))
                .isInstanceOf(AiProviderUnavailableException.class)
                .hasMessageNotContaining("secret response");
        HttpServer redirect = server();
        redirect.createContext("/api/tags", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://example.com/");
            respond(exchange, 302, "");
        });
        redirect.start();
        assertThatThrownBy(() -> provider(redirect, Duration.ofSeconds(2)).installedModels("local"))
                .isInstanceOf(AiProviderApiException.class);
    }

    @Test void handlesTimeoutMalformedAndOversizedOutput() throws Exception {
        HttpServer slow = server();
        slow.createContext("/api/tags", exchange -> {
            try { Thread.sleep(300); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            respond(exchange, 200, "{\"models\":[]}");
        });
        slow.start();
        assertThatThrownBy(() -> provider(slow, Duration.ofMillis(50)).installedModels("local"))
                .isInstanceOf(AiProviderTimeoutException.class);

        HttpServer malformed = server();
        malformed.createContext("/api/tags", exchange -> respond(exchange, 200, "not-json"));
        malformed.start();
        assertThatThrownBy(() -> provider(malformed, Duration.ofSeconds(2)).installedModels("local"))
                .isInstanceOf(AiProviderMalformedResponseException.class);

        HttpServer oversized = server();
        oversized.createContext("/api/tags", exchange -> respond(exchange, 200, "x".repeat(130_000)));
        oversized.start();
        assertThatThrownBy(() -> provider(oversized, Duration.ofSeconds(2)).installedModels("local"))
                .isInstanceOf(AiProviderMalformedResponseException.class);
    }

    @Test void rejectsMalformedAndOversizedReviewBodiesAndMissingService() throws Exception {
        HttpServer invalid = server();
        invalid.createContext("/api/tags", exchange -> respond(exchange, 200,
                "{\"models\":[{\"name\":\"installed:latest\"}]}"));
        invalid.createContext("/api/chat", exchange -> respond(exchange, 200,
                "{\"message\":{\"content\":\"not-json\"}}"));
        invalid.start();
        assertThatThrownBy(() -> provider(invalid, Duration.ofSeconds(2)).review(
                ProgrammingLanguage.JAVA, "PRIVATE_SOURCE", "local", "installed:latest"))
                .isInstanceOf(AiProviderMalformedResponseException.class)
                .hasMessageNotContaining("PRIVATE_SOURCE");

        HttpServer huge = server();
        huge.createContext("/api/tags", exchange -> respond(exchange, 200,
                "{\"models\":[{\"name\":\"installed:latest\"}]}"));
        huge.createContext("/api/chat", exchange -> respond(exchange, 200, "x".repeat(260_000)));
        huge.start();
        assertThatThrownBy(() -> provider(huge, Duration.ofSeconds(2)).review(
                ProgrammingLanguage.JAVA, "PRIVATE_SOURCE", "local", "installed:latest"))
                .isInstanceOf(AiProviderMalformedResponseException.class);

        HttpServer missing = server();
        missing.createContext("/api/tags", exchange -> respond(exchange, 404, "not here"));
        missing.start();
        assertThatThrownBy(() -> provider(missing, Duration.ofSeconds(2)).installedModels("local"))
                .isInstanceOf(AiProviderApiException.class)
                .hasMessageNotContaining("not here");
    }

    private OllamaAiProvider provider(HttpServer server, Duration timeout) {
        return new OllamaAiProvider(mapper,
                new OllamaConnections("local|Local|http://127.0.0.1:" + server.getAddress().getPort()),
                "local", "installed:latest", Duration.ofSeconds(1), timeout, 2048, 8192);
    }

    private HttpServer server() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servers.add(server);
        return server;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
