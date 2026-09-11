package com.devlensai.backend.controller;

import com.devlensai.backend.dto.AnalysisResponse;
import com.devlensai.backend.dto.AnalysisHistoryResponse;
import com.devlensai.backend.dto.CreateAnalysisRequest;
import com.devlensai.backend.entity.User;
import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.service.AnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    public ResponseEntity<AnalysisResponse> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateAnalysisRequest request
    ) {
        AnalysisResponse response = analysisService.create(user, request);
        return ResponseEntity.created(URI.create("/api/analyses/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public AnalysisResponse findById(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return analysisService.findById(user, id);
    }

    @GetMapping
    public List<AnalysisResponse> findAll(@AuthenticationPrincipal User user) {
        return analysisService.findAllNewestFirst(user);
    }

    @GetMapping("/history")
    public AnalysisHistoryResponse history(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProgrammingLanguage language,
            @RequestParam(defaultValue = "newest") String sort
    ) {
        return analysisService.findHistory(user, page, size, search, language, sort);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        analysisService.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
