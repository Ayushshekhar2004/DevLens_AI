package com.devlensai.backend.dto;

import com.devlensai.backend.entity.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record RepositoryScanResponse(
        Long id, Long snapshotId, String parserVersion, String status, String stackSummary,
        Instant createdAt, int skippedFileCount, Map<String, Long> skipCounts,
        List<RepositoryModuleRecord> modules, List<FileMetadata> files,
        List<RepositorySymbolRecord> symbols, List<RepositoryImportRecord> imports,
        List<RepositoryDependencyEdgeRecord> dependencyEdges
) {
    public static RepositoryScanResponse from(RepositoryScan scan) {
        Map<String, Long> counts = new TreeMap<>();
        scan.getSkips().forEach(skip -> counts.merge(skip.reason(), 1L, Long::sum));
        return new RepositoryScanResponse(scan.getId(), scan.getSnapshot().getId(), scan.getParserVersion(),
                scan.getStatus(), scan.getStackSummary(), scan.getCreatedAt(), scan.getSkips().size(), counts,
                scan.getModules(), scan.getFiles().stream().map(FileMetadata::from).toList(),
                scan.getSymbols(), scan.getImports(), scan.getDependencyEdges());
    }

    public record FileMetadata(String relativePath, String language, String contentHash, int lineCount,
                               String parserStatus, String parserMode, String moduleRoot) {
        static FileMetadata from(RepositoryFileRecord file) {
            return new FileMetadata(file.relativePath(), file.language(), file.contentHash(), file.lineCount(),
                    file.parserStatus(), file.parserMode(), file.moduleRoot());
        }
    }
}
