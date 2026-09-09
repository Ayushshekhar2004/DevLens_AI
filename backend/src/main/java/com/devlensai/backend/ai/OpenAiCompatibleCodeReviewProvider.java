package com.devlensai.backend.ai;

import com.devlensai.backend.dto.CodeReviewResult;
import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.exception.AiProviderApiException;
import com.devlensai.backend.exception.AiProviderMalformedResponseException;
import com.devlensai.backend.exception.AiProviderTimeoutException;
import com.devlensai.backend.exception.AiProviderUnavailableException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OpenAiCompatibleCodeReviewProvider implements AiCodeReviewProvider {

    private static final String PROVIDER_NAME = "openai-compatible";
    private static final Set<String> RESULT_FIELDS = Set.of(
            "summary", "potentialBugs", "timeComplexity", "spaceComplexity",
            "edgeCases", "suggestions", "improvedCode"
    );
    private static final String SYSTEM_PROMPT = """
            You are a careful code reviewer. Analyze code without executing it. Return only one JSON object,
            with no Markdown fences or additional text. It must contain exactly these fields:
            summary (string), potentialBugs (array of strings), timeComplexity (string),
            spaceComplexity (string), edgeCases (array of strings), suggestions (array of strings),
            and improvedCode (string). Use empty arrays when no items apply. Do not add test cases.
            """;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final URI endpoint;
    private final String model;
    private final Duration timeout;

    public OpenAiCompatibleCodeReviewProvider(
            ObjectMapper objectMapper,
            String apiKey,
            String baseUrl,
            String model,
            Duration timeout
    ) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(timeout).build(), apiKey, baseUrl, model, timeout);
    }

    OpenAiCompatibleCodeReviewProvider(
            ObjectMapper objectMapper,
            HttpClient httpClient,
            String apiKey,
            String baseUrl,
            String model,
            Duration timeout
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("AI API key must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("AI model must not be blank");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("AI timeout must be greater than zero");
        }

        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.apiKey = apiKey;
        this.endpoint = chatCompletionsEndpoint(baseUrl);
        this.model = model;
        this.timeout = timeout;
    }

    @Override
    public CodeReviewResult review(ProgrammingLanguage language, String sourceCode) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createRequestBody(language, sourceCode)))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException exception) {
            throw new AiProviderTimeoutException("AI provider request timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderUnavailableException("AI provider request was interrupted", exception);
        } catch (IOException exception) {
            throw new AiProviderUnavailableException("AI provider is unavailable", exception);
        }

        if (response.statusCode() >= 500) {
            throw new AiProviderUnavailableException("AI provider is temporarily unavailable");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiProviderApiException("AI provider rejected the request with status " + response.statusCode());
        }

        return parseResponse(response.body());
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    private String createRequestBody(ProgrammingLanguage language, String sourceCode) {
        Map<String, Object> jsonSchema = Map.of(
                "name", "code_review",
                "strict", true,
                "schema", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", RESULT_FIELDS,
                        "properties", Map.of(
                                "summary", Map.of("type", "string"),
                                "potentialBugs", stringArraySchema(),
                                "timeComplexity", Map.of("type", "string"),
                                "spaceComplexity", Map.of("type", "string"),
                                "edgeCases", stringArraySchema(),
                                "suggestions", stringArraySchema(),
                                "improvedCode", Map.of("type", "string")
                        )
                )
        );
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", "Language: " + language + "\n\nSource code:\n" + sourceCode)
                ),
                "response_format", Map.of("type", "json_schema", "json_schema", jsonSchema)
        );

        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException exception) {
            throw new AiProviderApiException("Could not create AI provider request", exception);
        }
    }

    private CodeReviewResult parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isString() || content.stringValue().isBlank()) {
                throw malformed();
            }

            JsonNode result = objectMapper.readTree(content.stringValue());
            validateResult(result);
            return new CodeReviewResult(
                    requiredText(result, "summary"),
                    stringList(result, "potentialBugs"),
                    requiredText(result, "timeComplexity"),
                    requiredText(result, "spaceComplexity"),
                    stringList(result, "edgeCases"),
                    stringList(result, "suggestions"),
                    requiredText(result, "improvedCode")
            );
        } catch (JacksonException exception) {
            throw new AiProviderMalformedResponseException("AI provider returned malformed JSON", exception);
        }
    }

    private void validateResult(JsonNode result) {
        if (!result.isObject()) {
            throw malformed();
        }
        Set<String> actualFields = new java.util.HashSet<>();
        result.properties().forEach(entry -> actualFields.add(entry.getKey()));
        if (!actualFields.equals(RESULT_FIELDS)) {
            throw malformed();
        }
    }

    private String requiredText(JsonNode result, String field) {
        JsonNode value = result.get(field);
        if (value == null || !value.isString()) {
            throw malformed();
        }
        return value.stringValue();
    }

    private List<String> stringList(JsonNode result, String field) {
        JsonNode value = result.get(field);
        if (value == null || !value.isArray()) {
            throw malformed();
        }
        Iterator<JsonNode> values = value.iterator();
        java.util.ArrayList<String> items = new java.util.ArrayList<>();
        while (values.hasNext()) {
            JsonNode item = values.next();
            if (!item.isString()) {
                throw malformed();
            }
            items.add(item.stringValue());
        }
        return List.copyOf(items);
    }

    private static Map<String, Object> stringArraySchema() {
        return Map.of("type", "array", "items", Map.of("type", "string"));
    }

    private static URI chatCompletionsEndpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("AI base URL must not be blank");
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return URI.create(normalized).resolve("chat/completions");
    }

    private static AiProviderMalformedResponseException malformed() {
        return new AiProviderMalformedResponseException("AI provider response did not match the required schema");
    }
}
