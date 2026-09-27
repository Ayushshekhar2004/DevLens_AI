package com.devlensai.backend.service;

import com.devlensai.backend.dto.RepositoryRetrievalRequest;
import com.devlensai.backend.entity.*;
import com.devlensai.backend.exception.RepositoryAnalysisException;
import com.devlensai.backend.exception.RepositorySnapshotNotFoundException;
import com.devlensai.backend.repository.RepositoryScanRepository;
import com.devlensai.backend.repository.RepositorySnapshotRepository;
import com.devlensai.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:retrieval;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false", "app.jwt.secret=retrieval-test-secret-at-least-32-bytes",
        "app.ai.provider=mock", "app.repository-lifecycle.retention-days=0",
        "app.repository-retrieval.graph-depth=2", "app.repository-retrieval.candidate-limit=10",
        "app.repository-retrieval.max-snippets=5", "app.repository-retrieval.per-file-tokens=200",
        "app.repository-retrieval.context-tokens=1024", "app.repository-retrieval.instruction-reserve-tokens=128",
        "app.repository-retrieval.output-reserve-tokens=128", "app.repository-retrieval.safety-reserve-tokens=128",
        "app.repository-retrieval.line-window=5"
})
class RepositoryContextRetrievalServiceTest {
    @Autowired RepositoryContextRetrievalService retrieval;
    @Autowired RepositoryAnalysisPreparationService preparation;
    @Autowired UserRepository users;
    @Autowired RepositorySnapshotRepository snapshots;
    @Autowired RepositoryScanRepository scans;
    User owner; User other; RepositorySnapshot snapshot; String snapshotHash;
    Map<String, RepositoryFileRecord> fixtureFiles;

    @BeforeEach
    void setup() {
        owner = users.save(new User("Owner", "retrieve-owner-" + System.nanoTime() + "@test.local", "x".repeat(60)));
        other = users.save(new User("Other", "retrieve-other-" + System.nanoTime() + "@test.local", "x".repeat(60)));
        snapshot = fixture(owner);
        snapshotHash = preparation.snapshotHash(snapshot);
    }

    @Test
    void ranksControllerDependenciesTestsAndFrontendAheadOfDistractorDeterministically() {
        var request = request("SYMBOL", "OrderController", "trace order persistence request");
        RepositoryContextPack first = retrieval.retrieve(owner, snapshot.getId(), request);
        RepositoryContextPack second = retrieval.retrieve(owner, snapshot.getId(), request);

        assertThat(first).isEqualTo(second);
        List<String> paths = first.snippets().stream().map(RepositoryContextPack.Snippet::relativePath).toList();
        assertThat(paths).contains("backend/OrderController.java", "backend/OrderService.java",
                "backend/OrderRepository.java", "backend/OrderServiceTest.java", "frontend/OrderPage.tsx");
        assertThat(paths).doesNotContain("distractor/AnalyticsRepository.java");
        assertThat(first.snippets()).allSatisfy(snippet -> {
            assertThat(snippet.startLine()).isGreaterThanOrEqualTo(1);
            assertThat(snippet.endLine()).isGreaterThanOrEqualTo(snippet.startLine());
            assertThat(snippet.endLine()).isLessThanOrEqualTo(file(snippet.relativePath()).lineCount());
            assertThat(snippet.fileHash()).isEqualTo(file(snippet.relativePath()).contentHash());
        });
        assertThat(first.usedContextTokens()).isLessThanOrEqualTo(first.usableContextTokens());
        assertThat(retrieval.inspect(owner, snapshot.getId(), request).selected().getFirst().toString())
                .doesNotContain("class OrderController");
    }

    @Test
    void handlesDuplicateSymbolsCyclesUnresolvedUnsupportedAndBudgetPressure() {
        RepositoryContextPack duplicate = retrieval.retrieve(owner, snapshot.getId(), request("SYMBOL", "save", "locate save symbol"));
        List<String> saves = duplicate.snippets().stream().filter(v -> v.reasons().stream().anyMatch(r -> r.startsWith("EXACT_SYMBOL")))
                .map(RepositoryContextPack.Snippet::relativePath).toList();
        assertThat(saves).contains("backend/OrderRepository.java", "distractor/AnalyticsRepository.java");
        assertThat(saves).isSorted();

        RepositoryContextPack cycle = retrieval.retrieve(owner, snapshot.getId(), request("FILE", "frontend/A.ts", "follow cyclic import alias"));
        assertThat(cycle.snippets()).extracting(RepositoryContextPack.Snippet::relativePath)
                .contains("frontend/A.ts", "frontend/B.ts");
        assertThat(cycle.unresolvedRelationships()).anyMatch(value -> value.contains("@/missing") && value.contains("ALIAS_UNRESOLVED"));
        assertThat(cycle.excluded()).anyMatch(value -> value.relativePath().equals("assets/logo.bin")
                && value.reason().equals("NO_ELIGIBLE_REDACTED_CONTENT"));
        assertThat(cycle.snippets()).hasSizeLessThanOrEqualTo(5);
        assertThat(cycle.usedContextTokens()).isLessThanOrEqualTo(640);

        RepositoryContextPack pressure = retrieval.retrieve(owner, snapshot.getId(),
                request("TASK", "order repository frontend cycle service", "inspect related tests and persistence"));
        assertThat(pressure.truncated()).isTrue();
        assertThat(pressure.excluded()).anyMatch(value ->
                value.reason().matches("SNIPPET_COUNT_LIMIT|FINAL_CONTEXT_BUDGET"));
    }

    @Test
    void rejectsMaliciousStaleAndCrossOwnerRequestsBeforeContentSelection() {
        assertThatThrownBy(() -> retrieval.retrieve(owner, snapshot.getId(),
                request("FILE", "../../etc/passwd", "inspect"))).isInstanceOf(RepositoryAnalysisException.class);
        assertThatThrownBy(() -> retrieval.retrieve(owner, snapshot.getId(), new RepositoryRetrievalRequest(
                "FILE", "backend/OrderController.java", "inspect", "f".repeat(64))))
                .isInstanceOf(RepositoryAnalysisException.class).hasMessageContaining("stale");
        assertThatThrownBy(() -> retrieval.retrieve(other, snapshot.getId(), request("FILE",
                "backend/OrderController.java", "inspect"))).isInstanceOf(RepositorySnapshotNotFoundException.class);
    }

    private RepositoryRetrievalRequest request(String type, String target, String purpose) {
        return new RepositoryRetrievalRequest(type, target, purpose, snapshotHash);
    }

    private RepositoryFileRecord file(String path) {
        return fixtureFiles.get(path);
    }

    private RepositorySnapshot fixture(User user) {
        List<RepositoryFileRecord> files = List.of(
                file("backend/OrderController.java", "JAVA", "class OrderController {\n OrderService service;\n void createOrder() {}\n}"),
                file("backend/OrderService.java", "JAVA", "class OrderService {\n OrderRepository repository;\n void persistOrder() {}\n}"),
                file("backend/OrderRepository.java", "JAVA", "interface OrderRepository {\n void save();\n}"),
                file("backend/OrderServiceTest.java", "JAVA", "class OrderServiceTest {\n void persistsOrder() {}\n}"),
                file("frontend/OrderPage.tsx", "TSX", "export function OrderPage() { return fetch('/orders'); }"),
                file("frontend/A.ts", "TYPESCRIPT", "import { b } from './B';\nimport x from '@/missing';\nexport const a = b;"),
                file("frontend/B.ts", "TYPESCRIPT", "import { a } from './A';\nexport const b = a;"),
                file("distractor/AnalyticsRepository.java", "JAVA", "interface AnalyticsRepository { void save(); }"),
                new RepositoryFileRecord("assets/logo.bin", "UNSUPPORTED", "9".repeat(64), 1, "UNSUPPORTED", "NONE", "assets", null));
        fixtureFiles = files.stream().collect(Collectors.toMap(RepositoryFileRecord::relativePath, value -> value));
        List<RepositorySnapshotFile> snapshotFiles = files.stream().map(value -> new RepositorySnapshotFile(
                value.relativePath(), value.contentHash(), value.safeContent() == null ? 0 : value.safeContent().length())).toList();
        RepositorySnapshot value = snapshots.save(new RepositorySnapshot(user, UUID.randomUUID().toString(),
                "retrieval.zip", 500, snapshotFiles));
        List<RepositoryModuleRecord> modules = List.of(
                new RepositoryModuleRecord("backend", "backend", "JAVA", null),
                new RepositoryModuleRecord("frontend", "frontend", "FRONTEND", null),
                new RepositoryModuleRecord("distractor", "distractor", "JAVA", null));
        List<RepositorySymbolRecord> symbols = List.of(
                new RepositorySymbolRecord("backend/OrderController.java", "OrderController", "CLASS", 1, "HEURISTIC"),
                new RepositorySymbolRecord("backend/OrderService.java", "OrderService", "CLASS", 1, "HEURISTIC"),
                new RepositorySymbolRecord("backend/OrderRepository.java", "save", "METHOD", 2, "HEURISTIC"),
                new RepositorySymbolRecord("distractor/AnalyticsRepository.java", "save", "METHOD", 1, "HEURISTIC"));
        List<RepositoryDependencyEdgeRecord> edges = List.of(
                new RepositoryDependencyEdgeRecord("backend/OrderController.java", "backend/OrderService.java", "IMPORT", "INTERNAL"),
                new RepositoryDependencyEdgeRecord("backend/OrderService.java", "backend/OrderRepository.java", "IMPORT", "INTERNAL"),
                new RepositoryDependencyEdgeRecord("backend/OrderServiceTest.java", "backend/OrderService.java", "IMPORT", "INTERNAL"),
                new RepositoryDependencyEdgeRecord("frontend/OrderPage.tsx", "backend/OrderController.java", "API_REFERENCE", "INTERNAL"),
                new RepositoryDependencyEdgeRecord("frontend/A.ts", "./B", "IMPORT", "INTERNAL"),
                new RepositoryDependencyEdgeRecord("frontend/B.ts", "./A", "IMPORT", "INTERNAL"),
                new RepositoryDependencyEdgeRecord("frontend/A.ts", "@/missing", "IMPORT", "ALIAS_UNRESOLVED"));
        List<RepositoryImportRecord> imports = edges.stream().map(edge -> new RepositoryImportRecord(
                edge.fromPath(), edge.target(), 1, edge.resolutionStatus(), "HEURISTIC")).toList();
        scans.save(new RepositoryScan(value, user, "fixture-v1", "COMPLETED", "Java, TypeScript", modules,
                files, symbols, imports, edges, List.of()));
        return value;
    }

    private RepositoryFileRecord file(String path, String language, String content) {
        char seed = (char) ('a' + Math.floorMod(path.hashCode(), 6));
        return new RepositoryFileRecord(path, language, String.valueOf(seed).repeat(64), content.lines().toList().size(),
                "PARSED", "HEURISTIC", path.substring(0, path.indexOf('/')), content);
    }
}
