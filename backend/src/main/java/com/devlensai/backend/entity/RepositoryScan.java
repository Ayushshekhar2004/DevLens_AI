package com.devlensai.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "repository_scans", uniqueConstraints = @UniqueConstraint(columnNames = "snapshot_id"))
public class RepositoryScan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private RepositorySnapshot snapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "parser_version", nullable = false, length = 32)
    private String parserVersion;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "stack_summary", nullable = false, length = 512)
    private String stackSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection @CollectionTable(name = "repository_scan_modules", joinColumns = @JoinColumn(name = "scan_id"))
    private List<RepositoryModuleRecord> modules = new ArrayList<>();

    @ElementCollection @CollectionTable(name = "repository_scan_files", joinColumns = @JoinColumn(name = "scan_id"))
    private List<RepositoryFileRecord> files = new ArrayList<>();

    @ElementCollection @CollectionTable(name = "repository_scan_symbols", joinColumns = @JoinColumn(name = "scan_id"))
    private List<RepositorySymbolRecord> symbols = new ArrayList<>();

    @ElementCollection @CollectionTable(name = "repository_scan_imports", joinColumns = @JoinColumn(name = "scan_id"))
    private List<RepositoryImportRecord> imports = new ArrayList<>();

    @ElementCollection @CollectionTable(name = "repository_scan_dependency_edges", joinColumns = @JoinColumn(name = "scan_id"))
    private List<RepositoryDependencyEdgeRecord> dependencyEdges = new ArrayList<>();

    @ElementCollection @CollectionTable(name = "repository_scan_skips", joinColumns = @JoinColumn(name = "scan_id"))
    private List<RepositorySkipRecord> skips = new ArrayList<>();

    protected RepositoryScan() { }

    public RepositoryScan(RepositorySnapshot snapshot, User user, String parserVersion, String status,
                          String stackSummary, List<RepositoryModuleRecord> modules,
                          List<RepositoryFileRecord> files, List<RepositorySymbolRecord> symbols,
                          List<RepositoryImportRecord> imports,
                          List<RepositoryDependencyEdgeRecord> dependencyEdges,
                          List<RepositorySkipRecord> skips) {
        this.snapshot = snapshot;
        this.user = user;
        this.parserVersion = parserVersion;
        this.status = status;
        this.stackSummary = stackSummary;
        this.modules.addAll(modules);
        this.files.addAll(files);
        this.symbols.addAll(symbols);
        this.imports.addAll(imports);
        this.dependencyEdges.addAll(dependencyEdges);
        this.skips.addAll(skips);
    }

    @PrePersist void initializeCreatedAt() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public RepositorySnapshot getSnapshot() { return snapshot; }
    public String getParserVersion() { return parserVersion; }
    public String getStatus() { return status; }
    public String getStackSummary() { return stackSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public List<RepositoryModuleRecord> getModules() { return List.copyOf(modules); }
    public List<RepositoryFileRecord> getFiles() { return List.copyOf(files); }
    public List<RepositorySymbolRecord> getSymbols() { return List.copyOf(symbols); }
    public List<RepositoryImportRecord> getImports() { return List.copyOf(imports); }
    public List<RepositoryDependencyEdgeRecord> getDependencyEdges() { return List.copyOf(dependencyEdges); }
    public List<RepositorySkipRecord> getSkips() { return List.copyOf(skips); }
}
