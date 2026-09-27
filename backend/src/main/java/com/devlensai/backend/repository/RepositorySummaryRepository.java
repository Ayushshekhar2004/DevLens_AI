package com.devlensai.backend.repository;

import com.devlensai.backend.entity.RepositorySummary;
import com.devlensai.backend.entity.RepositorySummaryLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface RepositorySummaryRepository extends JpaRepository<RepositorySummary, Long> {
    Optional<RepositorySummary> findFirstByUserIdAndCacheKeyAndStatusOrderByCreatedAtDesc(Long userId, String cacheKey, String status);
    List<RepositorySummary> findByJobIdOrderByIdAsc(Long jobId);
    List<RepositorySummary> findByJobIdAndLevelOrderByIdAsc(Long jobId, RepositorySummaryLevel level);
    List<RepositorySummary> findByJobSnapshotIdAndUserIdAndStatusOrderByCreatedAtDesc(
            Long snapshotId, Long userId, String status, Pageable pageable);
    void deleteByJobIdIn(Collection<Long> jobIds);
}
