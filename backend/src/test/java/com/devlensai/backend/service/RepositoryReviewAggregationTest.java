package com.devlensai.backend.service;

import com.devlensai.backend.dto.RepositoryEvidenceReference;
import com.devlensai.backend.dto.RepositoryFindingCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryReviewAggregationTest {
    private final RepositoryReviewService service = new RepositoryReviewService(null, null, null, null, null);

    @Test
    void deduplicatesOverlapsButKeepsDistinctSameLineDefectsAndStableOrder() {
        var location = new RepositoryEvidenceReference("src/OrderService.java", 10, 12);
        var duplicate = finding("a", "VALIDATION", "HIGH", "Caller omits order validation before save", location);
        var overlap = finding("b", "validation", "MEDIUM", "Caller omits validation before saving the order",
                new RepositoryEvidenceReference("src/OrderService.java", 11, 13));
        var distinct = finding("c", "ERROR_HANDLING", "CRITICAL", "Database exception is swallowed", location);

        var first = service.aggregate(List.of(duplicate, overlap, distinct));
        var second = service.aggregate(List.of(distinct, overlap, duplicate));

        assertThat(first).isEqualTo(second).hasSize(2);
        assertThat(first.getFirst().category()).isEqualTo("ERROR_HANDLING");
        assertThat(first.get(1).confidence()).contains("corroborated overlapping evidence");
    }

    @Test
    void preservesContradictoryConclusionsAsExplicitUncertainty() {
        var location = new RepositoryEvidenceReference("src/Controller.java", 4, 4);
        var unsafe = finding("a", "CONTRACT", "HIGH", "Input is validated before service call", location);
        var safe = finding("b", "CONTRACT", "HIGH", "Input is not validated before service call", location);

        var result = service.aggregate(List.of(unsafe, safe));

        assertThat(result).hasSize(2).allSatisfy(value ->
                assertThat(value.confidence()).startsWith("CONFLICTING_EVIDENCE:"));
    }

    @Test
    void rejectsInventedPrimaryEvidenceAndSanitizesInjectedModelText() {
        var pack = new RepositoryContextPack("review", "a".repeat(64), 500, 20, false, false,
                List.of(new RepositoryContextPack.Snippet("src/Main.java", "b".repeat(64), 1, 10,
                        "class Main {}", 20, 1000, List.of("EXACT_PATH"), null)), List.of(), List.of());
        var invented = candidate(new RepositoryEvidenceReference("src/Missing.java", 1, 1), List.of());
        assertThat(service.validateCandidate(invented, pack)).isEmpty();
        var invalidSeverity = new RepositoryFindingCandidate("contract", "URGENT", "qualitative", "claim",
                "rationale", new RepositoryEvidenceReference("src/Main.java", 2, 2), List.of(), "fix");
        assertThat(service.validateCandidate(invalidSeverity, pack)).isEmpty();

        var valid = candidate(new RepositoryEvidenceReference("src/Main.java", 2, 2),
                List.of(new RepositoryEvidenceReference("src/Missing.java", 1, 1)));
        assertThat(service.validateCandidate(valid, pack)).hasValueSatisfying(value -> {
            assertThat(value.severity()).isEqualTo("MEDIUM");
            assertThat(value.confidence()).startsWith("UNCERTAIN:");
            assertThat(value.claim()).contains("&lt;script&gt;").doesNotContain("<script>", "javascript:");
        });
    }

    private RepositoryReviewService.ValidatedFinding finding(String id, String category, String severity,
            String claim, RepositoryEvidenceReference primary) {
        return new RepositoryReviewService.ValidatedFinding(id, category.toUpperCase(), severity, "QUALITATIVE",
                claim, "Synthetic rationale", primary, List.of(), "Review the contract", "AI");
    }

    private RepositoryFindingCandidate candidate(RepositoryEvidenceReference primary,
            List<RepositoryEvidenceReference> related) {
        return new RepositoryFindingCandidate("contract", "HIGH", "qualitative",
                "<script>javascript:alert(1)</script>", "Synthetic rationale", primary, related,
                "javascript:fix()");
    }
}
