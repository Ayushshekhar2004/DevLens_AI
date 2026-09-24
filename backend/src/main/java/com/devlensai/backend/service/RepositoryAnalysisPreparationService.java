package com.devlensai.backend.service;

import com.devlensai.backend.entity.RepositoryFileRecord;
import com.devlensai.backend.entity.RepositoryModuleRecord;
import com.devlensai.backend.entity.RepositoryDependencyEdgeRecord;
import com.devlensai.backend.entity.RepositorySnapshot;
import com.devlensai.backend.entity.User;
import com.devlensai.backend.exception.RepositoryAnalysisException;
import com.devlensai.backend.exception.RepositorySnapshotNotFoundException;
import com.devlensai.backend.repository.RepositoryScanRepository;
import com.devlensai.backend.repository.RepositorySnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Service
public class RepositoryAnalysisPreparationService {
    private final RepositorySnapshotRepository snapshots;
    private final RepositoryScanRepository scans;

    public RepositoryAnalysisPreparationService(RepositorySnapshotRepository snapshots, RepositoryScanRepository scans) {
        this.snapshots = snapshots; this.scans = scans;
    }

    @Transactional(readOnly = true)
    public Prepared prepare(User user, Long snapshotId) {
        RepositorySnapshot snapshot = snapshots.findByIdAndUserId(snapshotId, user.getId())
                .orElseThrow(() -> new RepositorySnapshotNotFoundException(snapshotId));
        var scan = scans.findBySnapshotIdAndUserId(snapshotId, user.getId())
                .orElseThrow(() -> new RepositoryAnalysisException("Repository scan must complete before analysis"));
        if (!"COMPLETED".equals(scan.getStatus())) throw new RepositoryAnalysisException("Repository scan is not complete");
        List<RepositoryFileRecord> files = scan.getFiles().stream()
                .sorted(Comparator.comparing(RepositoryFileRecord::relativePath)).toList();
        return new Prepared(snapshot, snapshotHash(snapshot), scan.getParserVersion(), files,
                scan.getModules(), scan.getDependencyEdges());
    }

    public String snapshotHash(RepositorySnapshot snapshot) {
        StringBuilder canonical = new StringBuilder();
        snapshot.getFiles().stream().sorted(Comparator.comparing(file -> file.getRelativePath()))
                .forEach(file -> canonical.append(file.getRelativePath()).append('\0').append(file.getSha256()).append('\n'));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    public record Prepared(RepositorySnapshot snapshot, String snapshotHash, String parserVersion,
                           List<RepositoryFileRecord> files, List<RepositoryModuleRecord> modules,
                           List<RepositoryDependencyEdgeRecord> dependencyEdges) { }
}
