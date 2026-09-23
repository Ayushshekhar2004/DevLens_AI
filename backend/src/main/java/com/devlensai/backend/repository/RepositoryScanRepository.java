package com.devlensai.backend.repository;

import com.devlensai.backend.entity.RepositoryScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepositoryScanRepository extends JpaRepository<RepositoryScan, Long> {
    Optional<RepositoryScan> findBySnapshotIdAndUserId(Long snapshotId, Long userId);
    void deleteBySnapshotId(Long snapshotId);
}
