package com.devlensai.backend.controller;

import com.devlensai.backend.dto.*;
import com.devlensai.backend.entity.User;
import com.devlensai.backend.service.RepositoryAnalysisOrchestrator;
import com.devlensai.backend.service.RepositoryContextRetrievalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryAnalysisController {
    private final RepositoryAnalysisOrchestrator orchestrator;
    private final RepositoryContextRetrievalService retrieval;
    public RepositoryAnalysisController(RepositoryAnalysisOrchestrator orchestrator,
            RepositoryContextRetrievalService retrieval) { this.orchestrator = orchestrator; this.retrieval = retrieval; }

    @PostMapping("/snapshots/{snapshotId}/analysis-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RepositoryAnalysisJobResponse start(@AuthenticationPrincipal User user, @PathVariable Long snapshotId,
                                                @Valid @RequestBody StartRepositoryAnalysisRequest request) {
        return orchestrator.start(user, snapshotId, request);
    }

    @GetMapping("/analysis-jobs/{jobId}")
    public RepositoryAnalysisJobResponse status(@AuthenticationPrincipal User user, @PathVariable Long jobId) {
        return orchestrator.status(user, jobId);
    }

    @PostMapping("/analysis-jobs/{jobId}/cancel")
    public RepositoryAnalysisJobResponse cancel(@AuthenticationPrincipal User user, @PathVariable Long jobId) {
        return orchestrator.cancel(user, jobId);
    }

    @GetMapping("/analysis-jobs/{jobId}/units")
    public RepositoryInventoryResponse.Page<RepositoryAnalysisUnitResponse> units(
            @AuthenticationPrincipal User user, @PathVariable Long jobId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return orchestrator.units(user, jobId, page, size);
    }

    @GetMapping("/analysis-jobs/{jobId}/summaries")
    public java.util.List<RepositorySummaryResponse> summaries(
            @AuthenticationPrincipal User user, @PathVariable Long jobId) {
        return orchestrator.summaries(user, jobId);
    }

    @GetMapping("/analysis-jobs/{jobId}/report")
    public RepositoryReportResponse report(@AuthenticationPrincipal User user, @PathVariable Long jobId) {
        return orchestrator.report(user, jobId);
    }

    @PostMapping("/snapshots/{snapshotId}/context-inspection")
    public RepositoryRetrievalInspectionResponse inspect(@AuthenticationPrincipal User user,
            @PathVariable Long snapshotId, @Valid @RequestBody RepositoryRetrievalRequest request) {
        return retrieval.inspect(user, snapshotId, request);
    }
}
