package com.devlensai.backend.repository;

import com.devlensai.backend.entity.RepositoryAnalysisUnit;
import com.devlensai.backend.entity.RepositoryAnalysisUnitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public interface RepositoryAnalysisUnitRepository extends JpaRepository<RepositoryAnalysisUnit, Long> {
    List<RepositoryAnalysisUnit> findByJobIdOrderBySequenceAsc(Long jobId);
    Page<RepositoryAnalysisUnit> findByJobId(Long jobId, Pageable pageable);
    long countByJobIdAndStatusIn(Long jobId, Collection<RepositoryAnalysisUnitStatus> statuses);
    void deleteByJobIdIn(Collection<Long> jobIds);
}
