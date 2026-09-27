package com.devlensai.backend.repository;

import com.devlensai.backend.entity.RepositoryReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RepositoryReportRepository extends JpaRepository<RepositoryReport, Long> {
    Optional<RepositoryReport> findByJobIdAndJobUserId(Long jobId, Long userId);
    void deleteByJobIdIn(java.util.Collection<Long> jobIds);
}
