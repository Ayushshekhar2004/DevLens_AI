package com.devlensai.backend.repository;

import com.devlensai.backend.entity.RepositorySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepositorySnapshotRepository extends JpaRepository<RepositorySnapshot, Long> {
    Optional<RepositorySnapshot> findByIdAndUserId(Long id, Long userId);
}
