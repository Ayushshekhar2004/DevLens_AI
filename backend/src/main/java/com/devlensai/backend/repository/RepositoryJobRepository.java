package com.devlensai.backend.repository;

import com.devlensai.backend.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.time.Instant;
import java.util.Optional;

public interface RepositoryJobRepository extends JpaRepository<RepositoryJob, Long> {
    Optional<RepositoryJob> findByIdAndUserId(Long id, Long userId);
    Optional<RepositoryJob> findFirstBySnapshotIdAndTypeAndStatusInOrderByCreatedAtDesc(
            Long snapshotId, RepositoryJobType type, Collection<RepositoryJobStatus> statuses);
    java.util.List<RepositoryJob> findByStatusIn(Collection<RepositoryJobStatus> statuses);
    void deleteBySnapshotId(Long snapshotId);
    void deleteBySnapshotIsNullAndCreatedAtBefore(Instant cutoff);
    long countBySnapshotId(Long snapshotId);
}
