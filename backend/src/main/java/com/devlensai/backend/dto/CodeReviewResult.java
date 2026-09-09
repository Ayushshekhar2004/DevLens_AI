package com.devlensai.backend.dto;

import java.util.List;

public record CodeReviewResult(
        String summary,
        List<String> potentialBugs,
        String timeComplexity,
        String spaceComplexity,
        List<String> edgeCases,
        List<String> suggestions,
        String improvedCode
) {

    public CodeReviewResult {
        potentialBugs = List.copyOf(potentialBugs);
        edgeCases = List.copyOf(edgeCases);
        suggestions = List.copyOf(suggestions);
    }
}
