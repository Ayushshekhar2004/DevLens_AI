package com.devlensai.backend.controller;

import com.devlensai.backend.ai.AiCodeReviewProvider;
import com.devlensai.backend.ai.OllamaAiProvider;
import com.devlensai.backend.ai.OllamaConnections;
import com.devlensai.backend.exception.AiProviderApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai/ollama")
public class OllamaConnectionController {
    private final AiCodeReviewProvider provider;

    public OllamaConnectionController(AiCodeReviewProvider provider) {
        this.provider = provider;
    }

    @GetMapping("/profiles")
    public List<ProfileResponse> profiles() {
        if (!(provider instanceof OllamaAiProvider ollama)) return List.of();
        return ollama.profiles().stream().map(profile ->
                new ProfileResponse(profile.id(), profile.displayName())).toList();
    }

    @PostMapping("/profiles/{id}/test")
    public ConnectionResponse test(@PathVariable String id) {
        if (!(provider instanceof OllamaAiProvider ollama)) {
            throw new AiProviderApiException("Ollama provider is not enabled");
        }
        return new ConnectionResponse(ollama.installedModels(id));
    }

    public record ProfileResponse(String id, String displayName) { }
    public record ConnectionResponse(List<String> models) { }
}
