package com.devlensai.backend.repository;

import com.devlensai.backend.entity.RepositoryAnalysisStage;
import com.devlensai.backend.entity.RepositoryAnalysisStageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface RepositoryAnalysisStageRepository extends JpaRepository<RepositoryAnalysisStage, Long> {
    Optional<RepositoryAnalysisStage> findByJobIdAndType(Long jobId, RepositoryAnalysisStageType type);
    List<RepositoryAnalysisStage> findByJobIdOrderByIdAsc(Long jobId);
    void deleteByJobIdIn(Collection<Long> jobIds);
}
