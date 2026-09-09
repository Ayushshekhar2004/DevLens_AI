package com.devlensai.backend.service;

import com.devlensai.backend.dto.AnalysisResponse;
import com.devlensai.backend.dto.CreateAnalysisRequest;
import com.devlensai.backend.entity.Analysis;
import com.devlensai.backend.exception.AnalysisNotFoundException;
import com.devlensai.backend.repository.AnalysisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnalysisService {

    private final AnalysisRepository analysisRepository;

    public AnalysisService(AnalysisRepository analysisRepository) {
        this.analysisRepository = analysisRepository;
    }

    @Transactional
    public AnalysisResponse create(CreateAnalysisRequest request) {
        Analysis analysis = new Analysis(request.language(), request.sourceCode());
        analysis.markCompleted();
        return toResponse(analysisRepository.save(analysis));
    }

    @Transactional(readOnly = true)
    public AnalysisResponse findById(Long id) {
        return analysisRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new AnalysisNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<AnalysisResponse> findAllNewestFirst() {
        return analysisRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private AnalysisResponse toResponse(Analysis analysis) {
        return new AnalysisResponse(
                analysis.getId(),
                analysis.getProgrammingLanguage(),
                analysis.getSourceCode(),
                analysis.getStatus(),
                analysis.getCreatedAt()
        );
    }
}
