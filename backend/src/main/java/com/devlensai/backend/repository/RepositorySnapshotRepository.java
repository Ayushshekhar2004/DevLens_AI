package com.devlensai.backend.repository;

import com.devlensai.backend.entity.RepositorySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface RepositorySnapshotRepository extends JpaRepository<RepositorySnapshot, Long> {
    Optional<RepositorySnapshot> findByIdAndUserId(Long id, Long userId);
    Page<RepositorySnapshot> findAllByUserId(Long userId, Pageable pageable);
    List<RepositorySnapshot> findByCreatedAtBefore(Instant cutoff);
}
