package com.devlensai.backend.dto;

import java.util.List;

public record AnalysisHistoryResponse(
        List<AnalysisResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
