package com.devlensai.backend.repository;

import com.devlensai.backend.entity.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.entity.SecuritySeverity;

import java.util.List;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long>, JpaSpecificationExecutor<Analysis> {

    Optional<Analysis> findByIdAndUserId(Long id, Long userId);

    List<Analysis> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<Analysis> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("""
            select analysis.programmingLanguage as language, count(analysis) as total
            from Analysis analysis
            where analysis.user.id = :userId
            group by analysis.programmingLanguage
            """)
    List<LanguageCount> countAnalysesByLanguage(@Param("userId") Long userId);

    @Query("""
            select count(testCase)
            from Analysis analysis join analysis.generatedTestCases testCase
            where analysis.user.id = :userId
            """)
    long countGeneratedTestCasesByUserId(@Param("userId") Long userId);

    @Query("""
            select finding.severity as severity, count(finding) as total
            from Analysis analysis join analysis.securityFindings finding
            where analysis.user.id = :userId
            group by finding.severity
            """)
    List<SecuritySeverityCount> countSecurityFindingsBySeverity(@Param("userId") Long userId);

    interface LanguageCount {
        ProgrammingLanguage getLanguage();

        long getTotal();
    }

    interface SecuritySeverityCount {
        SecuritySeverity getSeverity();

        long getTotal();
    }
}
