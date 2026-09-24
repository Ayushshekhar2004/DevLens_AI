package com.devlensai.backend.repository;

import com.devlensai.backend.entity.RepositoryAnalysisJob;
import com.devlensai.backend.entity.RepositoryAnalysisJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RepositoryAnalysisJobRepository extends JpaRepository<RepositoryAnalysisJob, Long> {
    Optional<RepositoryAnalysisJob> findByIdAndUserId(Long id, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RepositoryAnalysisJob> findLockedById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RepositoryAnalysisJob> findLockedByIdAndUserId(Long id, Long userId);
    Optional<RepositoryAnalysisJob> findFirstBySnapshotIdAndProfileIdAndModelNameAndSnapshotHashAndStatusInOrderByCreatedAtDesc(
            Long snapshotId, String profileId, String modelName, String snapshotHash,
            Collection<RepositoryAnalysisJobStatus> statuses);
    List<RepositoryAnalysisJob> findByStatusIn(Collection<RepositoryAnalysisJobStatus> statuses);
    List<RepositoryAnalysisJob> findBySnapshotId(Long snapshotId);
    void deleteBySnapshotId(Long snapshotId);
}
