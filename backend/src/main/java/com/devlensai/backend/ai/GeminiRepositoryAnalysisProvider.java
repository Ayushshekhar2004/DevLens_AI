package com.devlensai.backend.ai;

import com.devlensai.backend.config.RepositoryAnalysisProperties;
import com.devlensai.backend.dto.*;
import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Explicit cloud provider. The job controller requires consent before queuing work. */
@Component
@ConditionalOnProperty(name = "app.repository-analysis.provider", havingValue = "gemini")
public class GeminiRepositoryAnalysisProvider implements RepositoryAnalysisProvider {
    private static final URI ENDPOINT = URI.create("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions");
    private static final int MAX_BYTES = 256_000;
    private final ObjectMapper mapper;
    private final String key;
    private final String model;
    private final Duration timeout;
    private final int outputLimit;
    private final HttpClient client;
    private final URI endpoint;
    private final RepositoryAiProtocol protocol = new RepositoryAiProtocol();

    @org.springframework.beans.factory.annotation.Autowired
    public GeminiRepositoryAnalysisProvider(ObjectMapper mapper,
            @Value("${app.ai.api-key}") String key,
            @Value("${app.repository-analysis.model}") String model,
            RepositoryAnalysisProperties limits) {
        this(mapper, key, model, Duration.ofSeconds(limits.callTimeoutSeconds()),
                limits.outputReserveTokens(), ENDPOINT);
    }

    // Package-private endpoint override is solely for deterministic HTTP tests.
    GeminiRepositoryAnalysisProvider(ObjectMapper mapper, String key, String model,
            Duration timeout, int outputLimit, URI endpoint) {
        if (key == null || key.isBlank() || model == null || !model.startsWith("gemini-")) {
            throw new IllegalArgumentException("Gemini repository analysis requires a key and a Gemini model");
        }
        this.mapper = mapper; this.key = key; this.model = model; this.timeout = timeout;
        this.outputLimit = outputLimit; this.endpoint = endpoint;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public boolean requiresCloudConsent() { return true; }
    @Override public String providerName() { return "gemini"; }
    @Override public void validateSelection(String profileId, String selectedModel) {
        if (!"gemini".equals(profileId) || !model.equals(selectedModel)) {
            throw new RepositoryAnalysisException("Select the administrator-configured Gemini profile and model");
        }
    }
    @Override public CodeReviewResult analyze(ProgrammingLanguage language, String source,
            String profileId, String selectedModel) {
        validateSelection(profileId, selectedModel);
        return protocol.parseResult(request(RepositoryAiProtocol.PROMPT,
                "Language: " + language + "\n<untrusted_source>\n" + source + "\n</untrusted_source>"));
    }
    @Override public RepositorySummaryResult summarize(String level, String identity, String content,
            List<RepositoryEvidenceReference> evidence, String profileId, String selectedModel) {
        validateSelection(profileId, selectedModel);
        return protocol.summary(request(RepositoryAiProtocol.SUMMARY_PROMPT,
                "Level: " + level + "\nIdentity: " + identity + "\nAllowed evidence: " + evidence
                        + "\n<untrusted_repository_data>\n" + content + "\n</untrusted_repository_data>"));
    }
    @Override public RepositoryReviewResult reviewRepository(String target, String content,
            List<RepositoryEvidenceReference> evidence, String profileId, String selectedModel) {
        validateSelection(profileId, selectedModel);
        return protocol.review(request(RepositoryAiProtocol.REVIEW_PROMPT,
                "Target: " + target + "\nAllowed evidence: " + evidence
                        + "\n<untrusted_repository_context>\n" + content + "\n</untrusted_repository_context>"));
    }

    private JsonNode request(String system, String user) {
        String body = mapper.writeValueAsString(Map.of("model", model,
                "max_tokens", outputLimit, "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user))));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream stream = response.body()) {
                int status = response.statusCode();
                if (status == 429 || status >= 500) throw new AiProviderUnavailableException("Gemini is busy or quota is exhausted");
                if (status < 200 || status >= 300) throw new AiProviderApiException("Gemini rejected request with status " + status);
                byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw malformed();
                JsonNode choice = mapper.readTree(new String(bytes, StandardCharsets.UTF_8)).path("choices").path(0);
                if (!"stop".equals(choice.path("finish_reason").asText())) throw malformed();
                JsonNode content = choice.path("message").path("content");
                if (!content.isString() || content.stringValue().isBlank()) throw malformed();
                return mapper.readTree(content.stringValue());
            }
        } catch (HttpTimeoutException exception) {
            throw new AiProviderTimeoutException("Gemini request timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderUnavailableException("Gemini request interrupted");
        } catch (IOException exception) {
            throw new AiProviderUnavailableException("Gemini connection failed");
        } catch (tools.jackson.core.JacksonException exception) {
            throw malformed();
        }
    }
    private static AiProviderMalformedResponseException malformed() {
        return new AiProviderMalformedResponseException("Gemini returned invalid or truncated JSON");
    }
}
