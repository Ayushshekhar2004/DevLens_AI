package com.devlensai.backend.controller;

import com.devlensai.backend.dto.AnalysisResponse;
import com.devlensai.backend.dto.CreateAnalysisRequest;
import com.devlensai.backend.service.AnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ResponseEntity<AnalysisResponse> create(@Valid @RequestBody CreateAnalysisRequest request) {
        AnalysisResponse response = analysisService.create(request);
        return ResponseEntity.created(URI.create("/api/analyses/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public AnalysisResponse findById(@PathVariable Long id) {
        return analysisService.findById(id);
    }

    @GetMapping
    public List<AnalysisResponse> findAll() {
        return analysisService.findAllNewestFirst();
    }
}
