package com.devlensai.backend.service;

import com.devlensai.backend.config.RepositoryRetrievalProperties;
import com.devlensai.backend.dto.RepositoryRetrievalInspectionResponse;
import com.devlensai.backend.dto.RepositoryRetrievalRequest;
import com.devlensai.backend.entity.*;
import com.devlensai.backend.exception.RepositoryAnalysisException;
import com.devlensai.backend.repository.RepositorySummaryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class RepositoryContextRetrievalService {
    static final String SCORING_VERSION = "lexical-graph-v1";
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-z0-9_$]+");
    private final RepositoryAnalysisPreparationService preparation;
    private final RepositorySummaryRepository summaries;
    private final RepositoryRetrievalProperties properties;

    public RepositoryContextRetrievalService(RepositoryAnalysisPreparationService preparation,
            RepositorySummaryRepository summaries, RepositoryRetrievalProperties properties) {
        this.preparation = preparation; this.summaries = summaries; this.properties = properties;
    }

    @Transactional(readOnly = true)
    public RepositoryContextPack retrieve(User user, Long snapshotId, RepositoryRetrievalRequest request) {
        var prepared = preparation.prepare(user, snapshotId); // owner/snapshot filter is applied before ranking
        if (!prepared.snapshotHash().equalsIgnoreCase(request.snapshotHash())) {
            throw new RepositoryAnalysisException("Repository snapshot reference is stale");
        }
        validateTarget(request.targetType(), request.target());
        Map<String, RepositoryFileRecord> files = new TreeMap<>();
        prepared.files().forEach(file -> files.put(file.relativePath(), file));
        Map<String, Candidate> ranked = new TreeMap<>();
        files.values().forEach(file -> ranked.put(file.relativePath(), new Candidate(file)));
        List<String> unresolved = new ArrayList<>();
        List<String> terms = terms(request.target() + " " + request.purpose());
        Set<String> seeds = seed(request, prepared, ranked, terms);
        lexical(prepared, ranked, terms);
        traverseGraph(prepared, files, ranked, seeds, unresolved);
        moduleAndTests(prepared, ranked, seeds, terms);
        applySummaryHints(user, snapshotId, ranked, terms);

        List<Candidate> candidates = ranked.values().stream().filter(value -> value.score > 0)
                .sorted(Comparator.comparingInt(Candidate::score).reversed().thenComparing(value -> value.file.relativePath()))
                .limit(properties.candidateLimit()).toList();
        int budget = properties.usableContextTokens(), used = 0;
        List<RepositoryContextPack.Snippet> selected = new ArrayList<>();
        List<RepositoryContextPack.Decision> excluded = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (selected.size() >= properties.maxSnippets()) {
                excluded.add(new RepositoryContextPack.Decision(candidate.file.relativePath(), "SNIPPET_COUNT_LIMIT")); continue;
            }
            if (candidate.file.safeContent() == null || candidate.file.safeContent().isBlank()) {
                excluded.add(new RepositoryContextPack.Decision(candidate.file.relativePath(), "NO_ELIGIBLE_REDACTED_CONTENT")); continue;
            }
            LineSlice slice = slice(candidate);
            int tokens = estimateTokens(slice.content());
            if (tokens > properties.perFileTokens()) {
                slice = trim(slice, properties.perFileTokens()); tokens = estimateTokens(slice.content());
            }
            if (used + tokens > budget) {
                excluded.add(new RepositoryContextPack.Decision(candidate.file.relativePath(), "FINAL_CONTEXT_BUDGET")); continue;
            }
            selected.add(new RepositoryContextPack.Snippet(candidate.file.relativePath(), candidate.file.contentHash(),
                    slice.start(), slice.end(), slice.content(), tokens, candidate.score,
                    List.copyOf(candidate.reasons), candidate.summaryHint));
            used += tokens;
        }
        ranked.values().stream().filter(value -> value.score == 0).limit(50)
                .forEach(value -> excluded.add(new RepositoryContextPack.Decision(value.file.relativePath(),
                        value.file.safeContent() == null ? "UNSUPPORTED_OR_EXCLUDED_CONTENT" : "NOT_RELEVANT")));
        boolean missing = selected.isEmpty();
        boolean truncated = !excluded.isEmpty() && excluded.stream().anyMatch(value ->
                Set.of("SNIPPET_COUNT_LIMIT", "FINAL_CONTEXT_BUDGET").contains(value.reason()));
        List<String> boundedUnresolved = unresolved.stream().distinct().sorted()
                .limit(properties.candidateLimit()).toList();
        return new RepositoryContextPack(request.purpose(), prepared.snapshotHash(), budget, used, truncated, missing,
                List.copyOf(selected), List.copyOf(excluded), boundedUnresolved);
    }

    @Transactional(readOnly = true)
    public RepositoryRetrievalInspectionResponse inspect(User user, Long snapshotId, RepositoryRetrievalRequest request) {
        RepositoryContextPack pack = retrieve(user, snapshotId, request);
        return new RepositoryRetrievalInspectionResponse(pack.purpose(), pack.snapshotHash(), pack.usableContextTokens(),
                pack.usedContextTokens(), pack.truncated(), pack.missingContext(), pack.snippets().stream()
                .map(value -> new RepositoryRetrievalInspectionResponse.Selected(value.relativePath(), value.fileHash(),
                        value.startLine(), value.endLine(), value.estimatedTokens(), value.score(), value.reasons(), value.summaryHint())).toList(),
                pack.excluded().stream().map(value -> new RepositoryRetrievalInspectionResponse.Decision(value.relativePath(), value.reason())).toList(),
                pack.unresolvedRelationships());
    }

    private Set<String> seed(RepositoryRetrievalRequest request, RepositoryAnalysisPreparationService.Prepared prepared,
            Map<String, Candidate> ranked, List<String> terms) {
        Set<String> seeds = new TreeSet<>(); String target = request.target();
        if (request.targetType().equals("FILE")) add(ranked, target, 1000, "EXACT_PATH", 1, seeds);
        if (request.targetType().equals("MODULE")) prepared.modules().stream()
                .filter(module -> module.name().equalsIgnoreCase(target) || module.rootPath().equals(target))
                .forEach(module -> ranked.values().stream().filter(value -> inModule(value.file.relativePath(), module.rootPath()))
                        .forEach(value -> add(ranked, value.file.relativePath(), 800, "EXACT_MODULE", 1, seeds)));
        if (request.targetType().equals("SYMBOL")) prepared.symbols().stream().filter(symbol -> symbol.name().equals(target))
                .forEach(symbol -> { add(ranked, symbol.filePath(), 900, "EXACT_SYMBOL:" + symbol.kind(), symbol.line(), seeds); });
        if (request.targetType().equals("TASK")) ranked.values().stream().filter(value -> terms.stream().anyMatch(term ->
                value.file.relativePath().toLowerCase(Locale.ROOT).contains(term)))
                .forEach(value -> add(ranked, value.file.relativePath(), 300, "TASK_PATH_MATCH", 1, seeds));
        return seeds;
    }

    private void lexical(RepositoryAnalysisPreparationService.Prepared prepared, Map<String, Candidate> ranked, List<String> terms) {
        for (RepositorySymbolRecord symbol : prepared.symbols()) for (String term : terms) {
            if (symbol.name().toLowerCase(Locale.ROOT).contains(term)) add(ranked, symbol.filePath(), 400, "LEXICAL_SYMBOL:" + term, symbol.line(), null);
        }
        for (Candidate value : ranked.values()) {
            String path = value.file.relativePath().toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (path.contains(term)) value.add(250, "LEXICAL_PATH:" + term, 1);
                if (value.file.safeContent() != null) {
                    int line = firstLine(value.file.safeContent(), term);
                    if (line > 0) value.add(80, "LEXICAL_CONTENT:" + term, line);
                }
            }
        }
    }

    private void traverseGraph(RepositoryAnalysisPreparationService.Prepared prepared, Map<String, RepositoryFileRecord> files,
            Map<String, Candidate> ranked, Set<String> seeds, List<String> unresolved) {
        Map<String, Set<String>> graph = new TreeMap<>();
        for (RepositoryDependencyEdgeRecord edge : prepared.dependencyEdges()) {
            Optional<String> target = resolve(edge.fromPath(), edge.target(), edge.resolutionStatus(), files.keySet());
            if (target.isEmpty()) { unresolved.add(edge.fromPath() + " -> " + edge.target() + " [" + edge.resolutionStatus() + "]"); continue; }
            graph.computeIfAbsent(edge.fromPath(), ignored -> new TreeSet<>()).add(target.get());
            graph.computeIfAbsent(target.get(), ignored -> new TreeSet<>()).add(edge.fromPath());
        }
        record Node(String path, int depth) { }
        Deque<Node> queue = new ArrayDeque<>(); seeds.forEach(path -> queue.add(new Node(path, 0)));
        Map<String,Integer> visited = new HashMap<>();
        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();
            if (node.depth() >= properties.graphDepth() || visited.getOrDefault(node.path(), Integer.MAX_VALUE) <= node.depth()) continue;
            visited.put(node.path(), node.depth());
            for (String neighbor : graph.getOrDefault(node.path(), Set.of())) {
                int depth = node.depth() + 1;
                add(ranked, neighbor, Math.max(100, 550 - depth * 75), "DEPENDENCY_DEPTH_" + depth, 1, null);
                queue.addLast(new Node(neighbor, depth));
            }
        }
    }

    private void moduleAndTests(RepositoryAnalysisPreparationService.Prepared prepared, Map<String, Candidate> ranked,
            Set<String> seeds, List<String> terms) {
        Set<String> roots = new TreeSet<>();
        for (String seed : seeds) prepared.modules().stream().filter(module -> inModule(seed, module.rootPath()))
                .map(RepositoryModuleRecord::rootPath).forEach(roots::add);
        ranked.values().stream().filter(value -> roots.stream().anyMatch(root -> inModule(value.file.relativePath(), root)))
                .forEach(value -> value.add(200, "MODULE_ADJACENCY", 1));
        Set<String> stems = new TreeSet<>(terms);
        seeds.forEach(path -> stems.add(fileStem(path).toLowerCase(Locale.ROOT)));
        ranked.values().stream().filter(value -> isTest(value.file.relativePath()))
                .filter(value -> stems.stream().anyMatch(stem -> value.file.relativePath().toLowerCase(Locale.ROOT).contains(stem)
                        || value.file.safeContent() != null && value.file.safeContent().toLowerCase(Locale.ROOT).contains(stem)))
                .forEach(value -> value.add(475, "RELEVANT_TEST", 1));
    }

    private void applySummaryHints(User user, Long snapshotId, Map<String, Candidate> ranked, List<String> terms) {
        var hints = summaries.findByJobSnapshotIdAndUserIdAndStatusOrderByCreatedAtDesc(snapshotId, user.getId(), "COMPLETED", PageRequest.of(0, 100));
        for (RepositorySummary hint : hints) {
            Candidate candidate = ranked.get(hint.getIdentity());
            if (candidate == null) continue;
            String text = (hint.getResponsibilities() + " " + hint.getKeySymbols() + " " + hint.getDependencies()).toLowerCase(Locale.ROOT);
            if (terms.stream().anyMatch(text::contains)) {
                candidate.add(125, "SUMMARY_HINT", 1);
                candidate.summaryHint = compact(hint.getResponsibilities(), 240);
            }
        }
    }

    private Optional<String> resolve(String from, String target, String status, Set<String> paths) {
        if (!"INTERNAL".equals(status)) return Optional.empty();
        if (paths.contains(target)) return Optional.of(target);
        if (target.startsWith(".")) {
            String parent = from.contains("/") ? from.substring(0, from.lastIndexOf('/')) : "";
            try {
                String normalized = Path.of(parent).resolve(target).normalize().toString().replace('\\', '/');
                for (String suffix : List.of("", ".ts", ".tsx", ".js", ".jsx", "/index.ts", "/index.tsx", "/index.js", "/index.jsx"))
                    if (paths.contains(normalized + suffix)) return Optional.of(normalized + suffix);
            } catch (InvalidPathException ignored) {
                return Optional.empty();
            }
        } else {
            String suffix = target.replace('.', '/') + ".java";
            return paths.stream().filter(path -> path.endsWith(suffix)).sorted().findFirst();
        }
        return Optional.empty();
    }

    private LineSlice slice(Candidate candidate) {
        String[] lines = candidate.file.safeContent().split("\\R", -1);
        int focus = candidate.lines.isEmpty() ? 1 : candidate.lines.first();
        int half = properties.lineWindow() / 2;
        int start = Math.max(1, focus - half), end = Math.min(lines.length, start + properties.lineWindow() - 1);
        start = Math.max(1, end - properties.lineWindow() + 1);
        return new LineSlice(start, end, String.join("\n", Arrays.copyOfRange(lines, start - 1, end)));
    }
    private LineSlice trim(LineSlice slice, int maxTokens) {
        int maxChars = Math.max(1, maxTokens * 3 - 16); String content = slice.content();
        if (content.length() <= maxChars) return slice;
        String bounded = content.substring(0, maxChars); int lines = (int) bounded.chars().filter(ch -> ch == '\n').count();
        return new LineSlice(slice.start(), Math.min(slice.end(), slice.start() + lines), bounded);
    }
    private void validateTarget(String type, String target) {
        if ((type.equals("FILE") || type.equals("MODULE")) && (target.startsWith("/") || target.startsWith("\\")
                || target.contains("\\") || target.contains("\0") || target.matches("^[A-Za-z]:.*")
                || Arrays.asList(target.split("/", -1)).contains(".."))) {
            throw new RepositoryAnalysisException("Invalid repository retrieval target");
        }
    }
    private void add(Map<String,Candidate> ranked, String path, int score, String reason, int line, Set<String> seeds) {
        Candidate value = ranked.get(path); if (value == null) return; value.add(score, reason, line); if (seeds != null) seeds.add(path);
    }
    private List<String> terms(String value) { return Arrays.stream(TOKEN_SPLIT.split(value.toLowerCase(Locale.ROOT))).filter(v -> v.length() >= 2).distinct().sorted().limit(12).toList(); }
    private int firstLine(String content, String term) { String lower = content.toLowerCase(Locale.ROOT); int at = lower.indexOf(term); return at < 0 ? 0 : 1 + (int) lower.substring(0, at).chars().filter(ch -> ch == '\n').count(); }
    private int estimateTokens(String value) { return Math.max(1, (value.length() + 2) / 3 + 16); }
    private boolean inModule(String path, String root) { return root == null || root.isBlank() || path.equals(root) || path.startsWith(root + "/"); }
    private boolean isTest(String path) { String lower = path.toLowerCase(Locale.ROOT); return lower.contains("/test/") || lower.contains("/tests/") || lower.matches(".*[._](test|spec)\\.[^.]+$"); }
    private String fileStem(String path) { String name = path.substring(path.lastIndexOf('/') + 1); int dot = name.indexOf('.'); return dot < 0 ? name : name.substring(0, dot); }
    private String compact(String value, int limit) { String safe = value == null ? "" : value.replaceAll("\\s+", " ").trim(); return safe.substring(0, Math.min(limit, safe.length())); }

    private static final class Candidate {
        final RepositoryFileRecord file; int score; final Set<String> reasons = new TreeSet<>(); final NavigableSet<Integer> lines = new TreeSet<>(); String summaryHint;
        Candidate(RepositoryFileRecord file) { this.file = file; }
        void add(int points, String reason, int line) { score += points; reasons.add(reason); if (line > 0) lines.add(line); }
        int score() { return score; }
    }
    private record LineSlice(int start, int end, String content) { }
}
