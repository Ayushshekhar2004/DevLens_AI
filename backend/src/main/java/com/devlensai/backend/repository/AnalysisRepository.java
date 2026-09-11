package com.devlensai.backend.repository;

import com.devlensai.backend.entity.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long>, JpaSpecificationExecutor<Analysis> {

    Optional<Analysis> findByIdAndUserId(Long id, Long userId);

    List<Analysis> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
