package com.devlensai.backend.ai;

import com.devlensai.backend.exception.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class GeminiRepositoryAnalysisProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    @AfterEach void close() { if (server != null) server.stop(0); }
    GeminiRepositoryAnalysisProvider provider(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            calls.incrementAndGet();
            var request = mapper.readTree(exchange.getRequestBody().readAllBytes());
            assertThat(request.path("max_tokens").intValue()).isEqualTo(2048);
            assertThat(request.has("tools")).isFalse();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer fake-key");
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        return new GeminiRepositoryAnalysisProvider(mapper, "fake-key", "gemini-test", Duration.ofSeconds(2),
                2048, URI.create("http://localhost:" + server.getAddress().getPort() + "/chat/completions"));
    }
    String envelope(String result, String finish) {
        return mapper.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", finish,
                "message", Map.of("content", result)))));
    }
    @Test void parsesSummaryAndPinsSelectionBeforeAnyNetworkCall() throws Exception {
        var p = provider(200, envelope("{\"responsibilities\":\"Demo\",\"keySymbols\":[],\"dependencies\":[],\"uncertainty\":\"Limited\",\"evidence\":[]}", "stop"));
        assertThat(p.requiresCloudConsent()).isTrue();
        assertThatThrownBy(() -> p.summarize("file", "Demo.java", "code", List.of(), "local", "gemini-test"))
                .isInstanceOf(RepositoryAnalysisException.class);
        assertThat(calls.get()).isZero();
        assertThat(p.summarize("file", "Demo.java", "code", List.of(), "gemini", "gemini-test").responsibilities()).isEqualTo("Demo");
    }
    @Test void parsesReviewWithoutInventedFindings() throws Exception {
        var p = provider(200, envelope("{\"insufficientContext\":true,\"uncertainty\":\"No caller\",\"findings\":[]}", "stop"));
        assertThat(p.reviewRepository("Demo", "code", List.of(), "gemini", "gemini-test").insufficientContext()).isTrue();
    }
    @Test void rejectsTruncation() throws Exception {
        var p = provider(200, envelope("{}", "length"));
        assertThatThrownBy(() -> p.summarize("file", "Demo", "code", List.of(), "gemini", "gemini-test"))
                .isInstanceOf(AiProviderMalformedResponseException.class);
    }
    @Test void rejectsWrongShape() throws Exception {
        var p = provider(200, envelope("{}", "stop"));
        assertThatThrownBy(() -> p.summarize("file", "Demo", "code", List.of(), "gemini", "gemini-test"))
                .isInstanceOf(AiProviderMalformedResponseException.class);
    }
    @Test void doesNotRetryQuotaErrorsOrExposeProviderBody() throws Exception {
        var p = provider(429, "secret diagnostic");
        assertThatThrownBy(() -> p.summarize("file", "Demo", "code", List.of(), "gemini", "gemini-test"))
                .isInstanceOf(AiProviderUnavailableException.class).hasMessageNotContaining("secret diagnostic");
        assertThat(calls.get()).isEqualTo(1);
    }
}
