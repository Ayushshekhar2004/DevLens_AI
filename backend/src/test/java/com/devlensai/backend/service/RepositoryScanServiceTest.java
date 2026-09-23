package com.devlensai.backend.service;

import com.devlensai.backend.config.RepositoryImportProperties;
import com.devlensai.backend.config.RepositoryScanProperties;
import com.devlensai.backend.entity.*;
import com.devlensai.backend.exception.RepositorySnapshotNotFoundException;
import com.devlensai.backend.repository.RepositoryScanRepository;
import com.devlensai.backend.repository.RepositorySnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RepositoryScanServiceTest {
    @TempDir Path temporary;
    private Path storage;
    private RepositorySnapshotRepository snapshotRepository;
    private RepositoryScanRepository scanRepository;
    private User owner;
    private AtomicReference<RepositoryScan> saved;

    @BeforeEach
    void setUp() throws Exception {
        storage = Files.createDirectories(temporary.resolve("private"));
        snapshotRepository = mock(RepositorySnapshotRepository.class);
        scanRepository = mock(RepositoryScanRepository.class);
        owner = mock(User.class);
        when(owner.getId()).thenReturn(11L);
        saved = new AtomicReference<>();
        when(scanRepository.findBySnapshotIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(scanRepository.save(any())).thenAnswer(invocation -> {
            RepositoryScan scan = invocation.getArgument(0);
            setId(scan, 91L);
            saved.set(scan);
            return scan;
        });
    }

    @Test
    void scansJavaReactMixedModulesAndPersistsDeterministicReferences() throws Exception {
        RepositorySnapshot snapshot = snapshot(7L, "mixed", Map.of(
                "backend/pom.xml", "<project><dependencies><dependency><groupId>org.example</groupId><artifactId>lib</artifactId></dependency></dependencies></project>",
                "backend/src/Main.java", "package demo;\nimport java.util.List;\npublic class Main {\n  public void run() {}\n}\n",
                "frontend/package.json", "{\"dependencies\":{\"react\":\"ignored-version\"}}",
                "frontend/src/App.tsx", "import { util } from './util';\nimport alias from '@/alias';\nconst lazy = import('./lazy');\nexport function App() { const apiKey = 'sensitive'; return util(); }\n",
                "frontend/src/util.ts", "export const util = () => 1;\n",
                "database/schema.sql", "select 1;\n"));
        own(snapshot);

        var response = service().scanOwned(owner, 7L);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.stackSummary()).contains("Java", "Maven", "React", "SQL");
        assertThat(response.modules()).extracting(RepositoryModuleRecord::rootPath)
                .containsExactly("backend", "frontend");
        assertThat(response.files()).extracting(file -> file.relativePath())
                .containsExactly("backend/pom.xml", "backend/src/Main.java", "database/schema.sql",
                        "frontend/package.json", "frontend/src/App.tsx", "frontend/src/util.ts");
        assertThat(response.symbols()).anySatisfy(symbol -> {
            assertThat(symbol.name()).isEqualTo("Main");
            assertThat(symbol.line()).isEqualTo(3);
            assertThat(symbol.extractionMode()).isEqualTo("HEURISTIC");
        });
        assertThat(response.imports()).anySatisfy(importRecord -> {
            assertThat(importRecord.filePath()).isEqualTo("frontend/src/App.tsx");
            assertThat(importRecord.specifier()).isEqualTo("./util");
            assertThat(importRecord.resolutionStatus()).isEqualTo("INTERNAL");
        });
        assertThat(response.imports()).anySatisfy(importRecord -> {
            assertThat(importRecord.specifier()).isEqualTo("@/alias");
            assertThat(importRecord.resolutionStatus()).isEqualTo("ALIAS_UNRESOLVED");
        }).anySatisfy(importRecord -> {
            assertThat(importRecord.specifier()).isEqualTo("./lazy");
            assertThat(importRecord.resolutionStatus()).isEqualTo("DYNAMIC_UNRESOLVED");
        });
        assertThat(response.dependencyEdges()).anySatisfy(edge -> {
            assertThat(edge.target()).isEqualTo("react");
            assertThat(edge.resolutionStatus()).isEqualTo("EXTERNAL_UNRESOLVED");
        });
        assertThat(saved.get().getFiles()).filteredOn(file -> file.relativePath().equals("frontend/src/App.tsx"))
                .singleElement().satisfies(file -> {
                    assertThat(file.safeContent()).contains("apiKey = <redacted>");
                    assertThat(file.safeContent()).doesNotContain("sensitive");
                });
    }

    @Test
    void appliesNestedIgnoreAndNegationWithoutOverridingSecurityExclusions() throws Exception {
        RepositorySnapshot snapshot = snapshot(8L, "ignored", Map.of(
                ".gitignore", "*.ts\n!keep.ts\n",
                ".devlensignore", "!node_modules/unsafe.ts\n",
                "keep.ts", "export const keep = 1;",
                "drop.ts", "DO_NOT_PERSIST_IGNORED",
                "nested/.devlensignore", "!allowed.ts\n",
                "nested/allowed.ts", "export const allowed = 1;",
                "nested/blocked.ts", "DO_NOT_PERSIST_NESTED",
                "node_modules/unsafe.ts", "DO_NOT_PERSIST_HARD_EXCLUDED"));
        own(snapshot);

        var response = service().scanOwned(owner, 8L);

        assertThat(response.files()).extracting(file -> file.relativePath())
                .contains("keep.ts", "nested/allowed.ts")
                .doesNotContain("drop.ts", "nested/blocked.ts", "node_modules/unsafe.ts");
        assertThat(response.skipCounts()).containsEntry("IGNORE_RULE", 2L)
                .containsEntry("SECURITY_EXCLUDED_DIRECTORY", 1L)
                .containsEntry("IGNORE_CONTROL_FILE", 3L);
        assertThat(saved.get().getFiles()).allSatisfy(file -> assertThat(file.safeContent())
                .doesNotContain("DO_NOT_PERSIST"));
    }

    @Test
    void recordsMalformedUnsupportedGeneratedAndUnsafeSecretFilesWithoutCrashing() throws Exception {
        RepositorySnapshot snapshot = snapshot(9L, "partial", Map.of(
                "package.json", "{malformed",
                "src/app.ts", "export const value = 1;",
                "src/generated.min.js", "DO_NOT_PERSIST_GENERATED",
                "notes.xyz", "unsupported text",
                "keys.properties", "-----BEGIN PRIVATE KEY-----\nDO_NOT_PERSIST_SECRET"));
        own(snapshot);

        var response = service().scanOwned(owner, 9L);

        assertThat(response.status()).isEqualTo("PARTIAL");
        assertThat(response.files()).filteredOn(file -> file.relativePath().equals("package.json"))
                .extracting(file -> file.parserStatus()).containsExactly("PARTIAL");
        assertThat(response.files()).filteredOn(file -> file.relativePath().equals("notes.xyz"))
                .extracting(file -> file.parserStatus()).containsExactly("UNSUPPORTED");
        assertThat(response.skipCounts()).containsEntry("GENERATED_OR_MINIFIED", 1L)
                .containsEntry("SUSPECT_SECRET_UNSAFE", 1L);
        assertThat(saved.get().getFiles()).allSatisfy(file -> {
            if (file.safeContent() != null) assertThat(file.safeContent()).doesNotContain("DO_NOT_PERSIST");
        });
    }

    @Test
    void returnsExistingScanAndKeepsMetadataStableForSameSnapshot() throws Exception {
        RepositorySnapshot snapshot = snapshot(10L, "stable", Map.of("src/A.java", "class A {}\n"));
        own(snapshot);
        var first = service().scanOwned(owner, 10L);
        when(scanRepository.findBySnapshotIdAndUserId(10L, 11L)).thenReturn(Optional.of(saved.get()));

        var second = service().scanOwned(owner, 10L);

        assertThat(second).isEqualTo(first);
        verify(scanRepository, times(1)).save(any());
    }

    @Test
    void neverExecutesImportedPackageHooks() throws Exception {
        Path marker = temporary.resolve("must-not-exist");
        RepositorySnapshot snapshot = snapshot(12L, "inert", Map.of(
                "package.json", "{\"scripts\":{\"postinstall\":\"touch " + marker + "\"}}",
                "src/index.ts", "export const ok = true;"));
        own(snapshot);

        service().scanOwned(owner, 12L);

        assertThat(marker).doesNotExist();
    }

    @Test
    void deniesCrossUserMetadataAccess() {
        User other = mock(User.class);
        when(other.getId()).thenReturn(22L);
        when(snapshotRepository.findByIdAndUserId(7L, 22L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().findOwned(other, 7L))
                .isInstanceOf(RepositorySnapshotNotFoundException.class);
        verify(scanRepository, never()).findBySnapshotIdAndUserId(7L, 22L);
    }

    private RepositoryScanService service() {
        var imports = new RepositoryImportProperties(storage.toString(), temporary.resolve("allowed").toString(),
                1_000_000, 1_000_000, 1_000_000, 1000, 20, 100, 30);
        var scans = new RepositoryScanProperties(1_000_000, 100_000, 100, 100);
        return new RepositoryScanService(snapshotRepository, scanRepository, imports, scans, new ObjectMapper());
    }

    private void own(RepositorySnapshot snapshot) {
        when(snapshotRepository.findByIdAndUserId(snapshot.getId(), 11L)).thenReturn(Optional.of(snapshot));
    }

    private RepositorySnapshot snapshot(long id, String key, Map<String, String> content) throws Exception {
        Path root = Files.createDirectories(storage.resolve(key).resolve("content"));
        List<RepositorySnapshotFile> files = new ArrayList<>();
        long total = 0;
        for (var entry : new TreeMap<>(content).entrySet()) {
            byte[] bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
            Path destination = root.resolve(entry.getKey());
            Files.createDirectories(destination.getParent());
            Files.write(destination, bytes);
            files.add(new RepositorySnapshotFile(entry.getKey(), hash(bytes), bytes.length));
            total += bytes.length;
        }
        RepositorySnapshot snapshot = new RepositorySnapshot(owner, key, key + ".zip", total, files);
        setId(snapshot, id);
        return snapshot;
    }

    private String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private void setId(Object target, long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
