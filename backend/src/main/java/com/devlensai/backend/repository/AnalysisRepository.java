package com.devlensai.backend.repository;

import com.devlensai.backend.entity.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    List<Analysis> findAllByOrderByCreatedAtDesc();
}
