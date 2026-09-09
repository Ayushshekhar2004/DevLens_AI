package com.devlensai.backend.ai;

import com.devlensai.backend.dto.CodeReviewResult;
import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.exception.AiProviderApiException;
import com.devlensai.backend.exception.AiProviderMalformedResponseException;
import com.devlensai.backend.exception.AiProviderTimeoutException;
import com.devlensai.backend.exception.AiProviderUnavailableException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleCodeReviewProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void sendsStructuredRequestAndParsesValidResult() throws Exception {
        String resultJson = """
                {"summary":"Looks good","potentialBugs":[],"timeComplexity":"O(1)",
                "spaceComplexity":"O(1)","edgeCases":["Empty input"],
                "suggestions":["Add documentation"],"improvedCode":"public class Main {}",
                "generatedTestCases":[{"name":"Basic construction","category":"NORMAL",
                "input":"Create a Main instance","expectedOutput":"Instance is created",
                "explanation":"Covers the implicit constructor","confidenceOrWarning":"High confidence"}]}
                """;
        String providerResponse = objectMapper.writeValueAsString(java.util.Map.of(
                "choices", List.of(java.util.Map.of(
                        "message", java.util.Map.of("content", resultJson)
                ))
        ));
        HttpServer server = server(exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-key");
            assertThat(requestBody).contains(
                    "json_schema",
                    "additionalProperties",
                    "generatedTestCases",
                    "Do not fabricate an expected output",
                    "JAVA",
                    "public class Main {}"
            );
            respond(exchange, 200, providerResponse);
        });

        CodeReviewResult result = provider(server, Duration.ofSeconds(2))
                .review(ProgrammingLanguage.JAVA, "public class Main {}");

        assertThat(result.summary()).isEqualTo("Looks good");
        assertThat(result.edgeCases()).containsExactly("Empty input");
        assertThat(result.improvedCode()).isEqualTo("public class Main {}");
        assertThat(result.generatedTestCases()).singleElement().satisfies(testCase -> {
            assertThat(testCase.name()).isEqualTo("Basic construction");
            assertThat(testCase.category().name()).isEqualTo("NORMAL");
            assertThat(testCase.confidenceOrWarning()).isEqualTo("High confidence");
        });
    }

    @Test
    void rejectsMalformedResultJson() throws Exception {
        String response = objectMapper.writeValueAsString(java.util.Map.of(
                "choices", List.of(java.util.Map.of(
                        "message", java.util.Map.of("content", "not-json")
                ))
        ));
        HttpServer server = server(exchange -> respond(exchange, 200, response));

        assertThatThrownBy(() -> provider(server, Duration.ofSeconds(2))
                .review(ProgrammingLanguage.PYTHON, "print('hello')"))
                .isInstanceOf(AiProviderMalformedResponseException.class);
    }

    @Test
    void rejectsUnsupportedTestCaseCategory() throws Exception {
        String resultJson = """
                {"summary":"Review","potentialBugs":[],"timeComplexity":"Unknown",
                "spaceComplexity":"Unknown","edgeCases":[],"suggestions":[],"improvedCode":"code",
                "generatedTestCases":[{"name":"Example","category":"SECURITY","input":"",
                "expectedOutput":"","explanation":"Example","confidenceOrWarning":"Uncertain"}]}
                """;
        String response = objectMapper.writeValueAsString(java.util.Map.of(
                "choices", List.of(java.util.Map.of(
                        "message", java.util.Map.of("content", resultJson)
                ))
        ));
        HttpServer server = server(exchange -> respond(exchange, 200, response));

        assertThatThrownBy(() -> provider(server, Duration.ofSeconds(2))
                .review(ProgrammingLanguage.JAVA, "code"))
                .isInstanceOf(AiProviderMalformedResponseException.class);
    }

    @Test
    void handlesRejectedApiRequestWithoutIncludingResponseBody() throws Exception {
        HttpServer server = server(exchange -> respond(exchange, 401, "secret diagnostic body"));

        assertThatThrownBy(() -> provider(server, Duration.ofSeconds(2))
                .review(ProgrammingLanguage.JAVA, "class Main {}"))
                .isInstanceOf(AiProviderApiException.class)
                .hasMessage("AI provider rejected the request with status 401")
                .hasMessageNotContaining("secret diagnostic body");
    }

    @Test
    void handlesUnavailableProvider() throws Exception {
        HttpServer server = server(exchange -> respond(exchange, 503, "unavailable"));

        assertThatThrownBy(() -> provider(server, Duration.ofSeconds(2))
                .review(ProgrammingLanguage.CPP, "int main() {}"))
                .isInstanceOf(AiProviderUnavailableException.class);
    }

    @Test
    void handlesProviderTimeout() throws Exception {
        HttpServer server = server(exchange -> {
            try {
                Thread.sleep(500);
                respond(exchange, 200, "{}");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });

        assertThatThrownBy(() -> provider(server, Duration.ofMillis(100))
                .review(ProgrammingLanguage.JAVASCRIPT, "const value = 1;"))
                .isInstanceOf(AiProviderTimeoutException.class);
    }

    private OpenAiCompatibleCodeReviewProvider provider(HttpServer server, Duration timeout) {
        return new OpenAiCompatibleCodeReviewProvider(
                objectMapper,
                "test-key",
                "http://localhost:" + server.getAddress().getPort() + "/v1/",
                "test-model",
                timeout
        );
    }

    private HttpServer server(ThrowingHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        servers.add(server);
        return server;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
