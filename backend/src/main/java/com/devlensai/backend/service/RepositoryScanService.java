package com.devlensai.backend.service;

import com.devlensai.backend.config.RepositoryImportProperties;
import com.devlensai.backend.config.RepositoryScanProperties;
import com.devlensai.backend.dto.RepositoryScanResponse;
import com.devlensai.backend.entity.*;
import com.devlensai.backend.exception.RepositoryScanException;
import com.devlensai.backend.exception.RepositorySnapshotNotFoundException;
import com.devlensai.backend.repository.RepositoryScanRepository;
import com.devlensai.backend.repository.RepositorySnapshotRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.BooleanSupplier;

@Service
public class RepositoryScanService {
    static final String PARSER_VERSION = "lexical-v1";
    private static final Set<String> HARD_DIRECTORIES = Set.of(
            ".git", ".hg", ".svn", "node_modules", "vendor", ".venv", "venv", "target", "build",
            "dist", ".next", "coverage", ".idea", ".vscode", ".cache", "__pycache__");
    private static final Set<String> LOCK_FILES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "npm-shrinkwrap.json", "gradle.lockfile");
    private static final Set<String> MANIFESTS = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "package.json");
    private static final Set<String> ARCHIVE_SUFFIXES = Set.of(
            ".zip", ".jar", ".war", ".tar", ".gz", ".tgz", ".7z", ".rar");
    private static final Pattern JAVA_IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$*][\\w$]*)*)\\s*;");
    private static final Pattern JS_IMPORT = Pattern.compile("(?m)(?:import|export)\\s+(?:[^;\\n]*?\\s+from\\s+)?['\"]([^'\"]+)['\"]|require\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)|import\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    private static final Pattern JAVA_SYMBOL = Pattern.compile("(?m)^\\s*(?:public|protected|private|abstract|final|static|sealed|non-sealed|strictfp|\\s)*\\b(class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern JAVA_METHOD = Pattern.compile("(?m)^\\s*(?:public|protected|private|static|final|synchronized|abstract|native|default|\\s)+[\\w<>,?\\[\\].]+\\s+([A-Za-z_$][\\w$]*)\\s*\\([^;{}]*\\)\\s*(?:throws\\s+[^\\{]+)?\\{");
    private static final Pattern JS_SYMBOL = Pattern.compile("(?m)^\\s*(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?(?:class|function|interface|type|enum)\\s+([A-Za-z_$][\\w$]*)|^\\s*(?:export\\s+)?(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile("(?im)\\b(password|passwd|secret|token|api[_-]?key|client[_-]?secret)\\b(\\s*[:=]\\s*)(['\"]?)([^\\s,'\"}]+)(['\"]?)");
    private static final Pattern AWS_ACCESS_KEY = Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b");

    private final RepositorySnapshotRepository snapshotRepository;
    private final RepositoryScanRepository scanRepository;
    private final RepositoryImportProperties importProperties;
    private final RepositoryScanProperties scanProperties;
    private final ObjectMapper objectMapper;

    public RepositoryScanService(RepositorySnapshotRepository snapshotRepository,
                                 RepositoryScanRepository scanRepository,
                                 RepositoryImportProperties importProperties,
                                 RepositoryScanProperties scanProperties,
                                 ObjectMapper objectMapper) {
        this.snapshotRepository = snapshotRepository;
        this.scanRepository = scanRepository;
        this.importProperties = importProperties;
        this.scanProperties = scanProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RepositoryScanResponse scanOwned(User user, Long snapshotId) {
        return scanOwned(user, snapshotId, () -> false);
    }

    @Transactional
    public RepositoryScanResponse scanOwned(User user, Long snapshotId, BooleanSupplier cancelled) {
        RepositorySnapshot snapshot = ownedSnapshot(user, snapshotId);
        Optional<RepositoryScan> existing = scanRepository.findBySnapshotIdAndUserId(snapshotId, user.getId());
        if (existing.isPresent()) return RepositoryScanResponse.from(existing.get());
        RepositoryScan scan = buildScan(snapshot, user, cancelled);
        try {
            return RepositoryScanResponse.from(scanRepository.save(scan));
        } catch (DataIntegrityViolationException race) {
            return scanRepository.findBySnapshotIdAndUserId(snapshotId, user.getId())
                    .map(RepositoryScanResponse::from).orElseThrow(() -> race);
        }
    }

    @Transactional(readOnly = true)
    public RepositoryScanResponse findOwned(User user, Long snapshotId) {
        ownedSnapshot(user, snapshotId);
        return scanRepository.findBySnapshotIdAndUserId(snapshotId, user.getId())
                .map(RepositoryScanResponse::from)
                .orElseThrow(() -> new RepositorySnapshotNotFoundException(snapshotId));
    }

    private RepositorySnapshot ownedSnapshot(User user, Long snapshotId) {
        return snapshotRepository.findByIdAndUserId(snapshotId, user.getId())
                .orElseThrow(() -> new RepositorySnapshotNotFoundException(snapshotId));
    }

    private RepositoryScan buildScan(RepositorySnapshot snapshot, User user, BooleanSupplier cancelled) {
        Path contentRoot = importProperties.storageRoot().resolve(snapshot.getStorageKey()).resolve("content").normalize();
        List<Candidate> candidates = new ArrayList<>();
        for (RepositorySnapshotFile metadata : snapshot.getFiles().stream()
                .sorted(Comparator.comparing(RepositorySnapshotFile::getRelativePath)).toList()) {
            ensureNotCancelled(cancelled);
            candidates.add(readCandidate(contentRoot, metadata));
        }
        List<IgnoreRule> ignoreRules = parseIgnoreRules(candidates);
        Map<String, Candidate> available = new TreeMap<>();
        candidates.forEach(candidate -> available.put(candidate.path(), candidate));

        List<RepositorySkipRecord> skips = new ArrayList<>();
        List<Candidate> eligible = new ArrayList<>();
        for (Candidate candidate : candidates) {
            ensureNotCancelled(cancelled);
            String hardReason = hardExclusion(candidate.path());
            if (hardReason != null) skips.add(new RepositorySkipRecord(candidate.path(), hardReason));
            else if (isIgnored(candidate.path(), ignoreRules)) skips.add(new RepositorySkipRecord(candidate.path(), "IGNORE_RULE"));
            else if (candidate.path().endsWith("/.gitignore") || candidate.path().equals(".gitignore")
                    || candidate.path().endsWith("/.devlensignore") || candidate.path().equals(".devlensignore")) {
                skips.add(new RepositorySkipRecord(candidate.path(), "IGNORE_CONTROL_FILE"));
            } else eligible.add(candidate);
        }

        ModuleResult moduleResult = detectModules(eligible);
        List<RepositoryFileRecord> files = new ArrayList<>();
        List<RepositorySymbolRecord> symbols = new ArrayList<>();
        List<RepositoryImportRecord> imports = new ArrayList<>();
        List<RepositoryDependencyEdgeRecord> edges = new ArrayList<>(moduleResult.dependencies());
        boolean partial = moduleResult.partial();

        for (Candidate candidate : eligible) {
            ensureNotCancelled(cancelled);
            String language = language(candidate.path());
            if (candidate.oversized()) {
                files.add(file(candidate, language, "PARTIAL", "NONE", moduleRoot(candidate.path(), moduleResult.modules()), null));
                partial = true;
                continue;
            }
            if (language.equals("UNSUPPORTED")) {
                files.add(file(candidate, language, "UNSUPPORTED", "NONE", moduleRoot(candidate.path(), moduleResult.modules()), null));
                partial = true;
                continue;
            }
            String text;
            try {
                text = decode(candidate.bytes());
            } catch (CharacterCodingException exception) {
                files.add(file(candidate, language, "PARTIAL", "NONE", moduleRoot(candidate.path(), moduleResult.modules()), null));
                partial = true;
                continue;
            }
            if (containsUnsafeSecret(text)) {
                skips.add(new RepositorySkipRecord(candidate.path(), "SUSPECT_SECRET_UNSAFE"));
                partial = true;
                continue;
            }
            String safe = redactSecrets(text);
            String parserStatus = moduleResult.malformedManifests().contains(candidate.path()) ? "PARTIAL" : "PARSED";
            if (parserStatus.equals("PARTIAL")) partial = true;
            files.add(file(candidate, language, parserStatus, parserMode(language),
                    moduleRoot(candidate.path(), moduleResult.modules()), safe));
            extractSymbols(candidate.path(), language, safe, symbols);
            extractImports(candidate.path(), language, safe, available.keySet(), imports, edges);
        }

        sortMetadata(moduleResult.modules(), files, symbols, imports, edges, skips);
        String status = partial ? "PARTIAL" : "COMPLETED";
        return new RepositoryScan(snapshot, user, PARSER_VERSION, status,
                String.join(", ", moduleResult.stack()), moduleResult.modules(), files, symbols, imports, edges, skips);
    }

    private Candidate readCandidate(Path root, RepositorySnapshotFile metadata) {
        Path file = root.resolve(metadata.getRelativePath()).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new RepositoryScanException("Snapshot content is unavailable");
        if (metadata.getSizeBytes() > scanProperties.maxTextBytes()) {
            return new Candidate(metadata.getRelativePath(), metadata.getSha256(), new byte[0], true);
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (!sha256(bytes).equals(metadata.getSha256())) throw new RepositoryScanException("Snapshot integrity check failed");
            return new Candidate(metadata.getRelativePath(), metadata.getSha256(), bytes, false);
        } catch (IOException exception) {
            throw new RepositoryScanException("Snapshot content could not be read safely", exception);
        }
    }

    private List<IgnoreRule> parseIgnoreRules(List<Candidate> candidates) {
        List<IgnoreRule> rules = new ArrayList<>();
        List<Candidate> ignoreFiles = candidates.stream().filter(candidate -> {
            String name = fileName(candidate.path());
            return name.equals(".gitignore") || name.equals(".devlensignore");
        }).sorted(Comparator.comparingInt((Candidate candidate) -> Path.of(parent(candidate.path())).getNameCount())
                .thenComparing(candidate -> parent(candidate.path()))
                .thenComparingInt(candidate -> fileName(candidate.path()).equals(".gitignore") ? 0 : 1)).toList();
        for (Candidate candidate : ignoreFiles) {
            String name = fileName(candidate.path());
            if (candidate.oversized() || candidate.bytes().length > scanProperties.maxManifestBytes()) continue;
            try {
                String base = parent(candidate.path());
                String[] lines = decode(candidate.bytes()).split("\\R", -1);
                for (String raw : lines) {
                    IgnoreRule rule = IgnoreRule.parse(base, raw);
                    if (rule != null) rules.add(rule);
                }
            } catch (CharacterCodingException ignored) {
                // Malformed ignore files are inert data and cannot broaden access.
            }
        }
        return List.copyOf(rules);
    }

    private boolean isIgnored(String path, List<IgnoreRule> rules) {
        boolean ignored = false;
        for (IgnoreRule rule : rules) if (rule.matches(path)) ignored = !rule.negated();
        return ignored;
    }

    private String hardExclusion(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        for (String part : lower.split("/")) if (HARD_DIRECTORIES.contains(part)) return "SECURITY_EXCLUDED_DIRECTORY";
        String name = fileName(lower);
        boolean configTemplate = name.contains("example") || name.contains("sample") || name.contains("template");
        if ((name.equals(".env") || name.startsWith(".env.")) && !configTemplate) return "SECURITY_SENSITIVE_FILE";
        if (name.matches(".*(?:credential|credentials|secret|secrets)(?:\\..*)?")
                || name.endsWith(".pem") || name.endsWith(".key") || name.endsWith(".p12")
                || name.endsWith(".pfx") || name.endsWith(".jks") || name.endsWith(".keystore")
                || name.endsWith(".sql.gz") || name.endsWith(".dump") || name.endsWith(".bak")
                || name.endsWith(".sqlite") || name.endsWith(".sqlite3") || name.endsWith(".db")) {
            return "SECURITY_SENSITIVE_FILE";
        }
        if (LOCK_FILES.contains(name)) return "LOCK_FILE";
        if (name.endsWith(".min.js") || name.endsWith(".min.css") || name.endsWith(".map")
                || name.contains(".generated.") || name.endsWith(".g.java")) return "GENERATED_OR_MINIFIED";
        for (String suffix : ARCHIVE_SUFFIXES) if (name.endsWith(suffix)) return "ARCHIVE";
        return null;
    }

    private ModuleResult detectModules(List<Candidate> candidates) {
        Map<String, RepositoryModuleRecord> modules = new TreeMap<>();
        Set<String> stack = new TreeSet<>();
        Set<String> malformed = new HashSet<>();
        List<RepositoryDependencyEdgeRecord> dependencies = new ArrayList<>();
        for (Candidate candidate : candidates) {
            String name = fileName(candidate.path());
            String root = parent(candidate.path());
            if (!MANIFESTS.contains(name) || candidate.oversized() || candidate.bytes().length > scanProperties.maxManifestBytes()) continue;
            if (name.equals("package.json")) {
                boolean react = false;
                boolean bad = false;
                try {
                    JsonNode json = objectMapper.readTree(candidate.bytes());
                    for (String field : List.of("dependencies", "devDependencies", "peerDependencies")) {
                        JsonNode section = json.get(field);
                        if (section != null && section.isObject()) for (String dependency : new TreeSet<>(section.propertyNames())) {
                            if (dependency.equals("react")) react = true;
                            dependencies.add(new RepositoryDependencyEdgeRecord(candidate.path(), dependency,
                                    "MANIFEST_DEPENDENCY", "EXTERNAL_UNRESOLVED"));
                        }
                    }
                } catch (RuntimeException exception) {
                    bad = true;
                    malformed.add(candidate.path());
                }
                String type = react ? "REACT" : "JAVASCRIPT_TYPESCRIPT";
                if (bad) type = "JAVASCRIPT_TYPESCRIPT_PARTIAL";
                modules.putIfAbsent(root, new RepositoryModuleRecord(moduleName(root), root, type, candidate.path()));
                stack.add(react ? "React" : "JavaScript/TypeScript");
            } else if (name.equals("pom.xml")) {
                boolean bad = !parsePomDependencies(candidate, dependencies);
                if (bad) malformed.add(candidate.path());
                modules.putIfAbsent(root, new RepositoryModuleRecord(moduleName(root), root,
                        bad ? "JAVA_MAVEN_PARTIAL" : "JAVA_MAVEN", candidate.path()));
                stack.add("Java"); stack.add("Maven");
            } else {
                modules.putIfAbsent(root, new RepositoryModuleRecord(moduleName(root), root, "JAVA_GRADLE", candidate.path()));
                stack.add("Java"); stack.add("Gradle");
            }
        }
        for (Candidate candidate : candidates) {
            String language = language(candidate.path());
            if (language.equals("JAVA")) stack.add("Java");
            if (Set.of("JAVASCRIPT", "TYPESCRIPT", "JSX", "TSX").contains(language)) stack.add("JavaScript/TypeScript");
            if (language.equals("SQL")) stack.add("SQL");
        }
        if (modules.isEmpty()) modules.put("", new RepositoryModuleRecord("root", "", "SOURCE", null));
        return new ModuleResult(new ArrayList<>(modules.values()), stack, dependencies, malformed, !malformed.isEmpty());
    }

    private boolean parsePomDependencies(Candidate candidate, List<RepositoryDependencyEdgeRecord> dependencies) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(candidate.bytes()));
            var dependencyNodes = document.getElementsByTagName("dependency");
            for (int index = 0; index < dependencyNodes.getLength(); index++) {
                var node = dependencyNodes.item(index);
                String group = childText(node, "groupId");
                String artifact = childText(node, "artifactId");
                if (!artifact.isBlank()) dependencies.add(new RepositoryDependencyEdgeRecord(candidate.path(),
                        group.isBlank() ? artifact : group + ":" + artifact, "MANIFEST_DEPENDENCY", "EXTERNAL_UNRESOLVED"));
            }
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private String childText(org.w3c.dom.Node node, String name) {
        for (int index = 0; index < node.getChildNodes().getLength(); index++) {
            var child = node.getChildNodes().item(index);
            if (child.getNodeName().equals(name)) return child.getTextContent().trim();
        }
        return "";
    }

    private RepositoryFileRecord file(Candidate candidate, String language, String status, String mode,
                                      String moduleRoot, String safeContent) {
        return new RepositoryFileRecord(candidate.path(), language, candidate.hash(),
                safeContent == null ? 0 : lineCount(safeContent), status, mode, moduleRoot, safeContent);
    }

    private void extractSymbols(String path, String language, String text, List<RepositorySymbolRecord> output) {
        if (language.equals("JAVA")) {
            addSymbols(path, text, JAVA_SYMBOL, output, true);
            addSymbols(path, text, JAVA_METHOD, output, false);
        } else if (Set.of("JAVASCRIPT", "TYPESCRIPT", "JSX", "TSX").contains(language)) {
            Matcher matcher = JS_SYMBOL.matcher(text);
            while (matcher.find() && countForPath(output, path) < scanProperties.maxSymbolsPerFile()) {
                String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                output.add(new RepositorySymbolRecord(path, name, "DECLARATION", lineAt(text, matcher.start()), "HEURISTIC"));
            }
        }
    }

    private void addSymbols(String path, String text, Pattern pattern, List<RepositorySymbolRecord> output, boolean typed) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find() && countForPath(output, path) < scanProperties.maxSymbolsPerFile()) {
            String kind = typed ? matcher.group(1).toUpperCase(Locale.ROOT) : "METHOD";
            String name = typed ? matcher.group(2) : matcher.group(1);
            output.add(new RepositorySymbolRecord(path, name, kind, lineAt(text, matcher.start()), "HEURISTIC"));
        }
    }

    private long countForPath(List<RepositorySymbolRecord> records, String path) {
        return records.stream().filter(record -> record.filePath().equals(path)).count();
    }

    private void extractImports(String path, String language, String text, Set<String> paths,
                                List<RepositoryImportRecord> imports, List<RepositoryDependencyEdgeRecord> edges) {
        Pattern pattern = language.equals("JAVA") ? JAVA_IMPORT
                : Set.of("JAVASCRIPT", "TYPESCRIPT", "JSX", "TSX").contains(language) ? JS_IMPORT : null;
        if (pattern == null) return;
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find() && count++ < scanProperties.maxImportsPerFile()) {
            String specifier = firstNonNull(matcher);
            boolean dynamic = !language.equals("JAVA") && matcher.group(3) != null;
            String resolution = dynamic ? "DYNAMIC_UNRESOLVED" : resolve(path, specifier, language, paths);
            imports.add(new RepositoryImportRecord(path, specifier, lineAt(text, matcher.start()), resolution, "HEURISTIC"));
            edges.add(new RepositoryDependencyEdgeRecord(path, specifier, dynamic ? "DYNAMIC_IMPORT" : "IMPORT", resolution));
        }
    }

    private String firstNonNull(Matcher matcher) {
        for (int index = 1; index <= matcher.groupCount(); index++) if (matcher.group(index) != null) return matcher.group(index);
        return "";
    }

    private String resolve(String source, String specifier, String language, Set<String> paths) {
        if (language.equals("JAVA")) {
            String suffix = specifier.replace('.', '/') + ".java";
            return paths.stream().anyMatch(path -> path.endsWith(suffix)) ? "INTERNAL" : "EXTERNAL_UNRESOLVED";
        }
        if (!specifier.startsWith(".")) return specifier.startsWith("@/") ? "ALIAS_UNRESOLVED" : "EXTERNAL_UNRESOLVED";
        Path base = Path.of(parent(source));
        String normalized = base.resolve(specifier).normalize().toString().replace('\\', '/');
        for (String suffix : List.of("", ".ts", ".tsx", ".js", ".jsx", "/index.ts", "/index.tsx", "/index.js", "/index.jsx")) {
            if (paths.contains(normalized + suffix)) return "INTERNAL";
        }
        return "LOCAL_UNRESOLVED";
    }

    private String language(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) return "JAVA";
        if (lower.endsWith(".tsx")) return "TSX";
        if (lower.endsWith(".ts")) return "TYPESCRIPT";
        if (lower.endsWith(".jsx")) return "JSX";
        if (lower.endsWith(".js") || lower.endsWith(".mjs") || lower.endsWith(".cjs")) return "JAVASCRIPT";
        if (lower.endsWith(".sql")) return "SQL";
        if (MANIFESTS.contains(fileName(lower)) || lower.endsWith(".json") || lower.endsWith(".yaml")
                || lower.endsWith(".yml") || lower.endsWith(".properties") || lower.endsWith(".toml")) return "CONFIG";
        return "UNSUPPORTED";
    }

    private String parserMode(String language) {
        return Set.of("JAVA", "JAVASCRIPT", "TYPESCRIPT", "JSX", "TSX").contains(language) ? "HEURISTIC" : "METADATA_ONLY";
    }

    private boolean containsUnsafeSecret(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        return upper.contains("-----BEGIN PRIVATE KEY-----") || upper.contains("-----BEGIN RSA PRIVATE KEY-----")
                || upper.contains("-----BEGIN OPENSSH PRIVATE KEY-----");
    }

    private String redactSecrets(String text) {
        String redacted = SECRET_ASSIGNMENT.matcher(text).replaceAll("$1$2<redacted>");
        redacted = AWS_ACCESS_KEY.matcher(redacted).replaceAll("<redacted-access-key>");
        return JWT.matcher(redacted).replaceAll("<redacted-token>");
    }

    private String decode(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private int lineCount(String text) {
        if (text.isEmpty()) return 0;
        int lines = 1;
        for (int index = 0; index < text.length(); index++) if (text.charAt(index) == '\n') lines++;
        return text.endsWith("\n") ? lines - 1 : lines;
    }

    private int lineAt(String text, int offset) {
        int line = 1;
        for (int index = 0; index < offset; index++) if (text.charAt(index) == '\n') line++;
        return line;
    }

    private String moduleRoot(String path, List<RepositoryModuleRecord> modules) {
        return modules.stream().map(RepositoryModuleRecord::rootPath)
                .filter(root -> root.isEmpty() || path.equals(root) || path.startsWith(root + "/"))
                .max(Comparator.comparingInt(String::length)).orElse("");
    }

    private void sortMetadata(List<RepositoryModuleRecord> modules, List<RepositoryFileRecord> files,
                              List<RepositorySymbolRecord> symbols, List<RepositoryImportRecord> imports,
                              List<RepositoryDependencyEdgeRecord> edges, List<RepositorySkipRecord> skips) {
        modules.sort(Comparator.comparing(RepositoryModuleRecord::rootPath));
        files.sort(Comparator.comparing(RepositoryFileRecord::relativePath));
        symbols.sort(Comparator.comparing(RepositorySymbolRecord::filePath).thenComparingInt(RepositorySymbolRecord::line).thenComparing(RepositorySymbolRecord::name));
        imports.sort(Comparator.comparing(RepositoryImportRecord::filePath).thenComparingInt(RepositoryImportRecord::line).thenComparing(RepositoryImportRecord::specifier));
        edges.sort(Comparator.comparing(RepositoryDependencyEdgeRecord::fromPath).thenComparing(RepositoryDependencyEdgeRecord::target).thenComparing(RepositoryDependencyEdgeRecord::kind));
        skips.sort(Comparator.comparing(RepositorySkipRecord::relativePath));
    }

    private String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private void ensureNotCancelled(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) {
            throw new RepositoryScanException("Repository scan was cancelled");
        }
    }

    private String fileName(String path) { int slash = path.lastIndexOf('/'); return slash < 0 ? path : path.substring(slash + 1); }
    private String parent(String path) { int slash = path.lastIndexOf('/'); return slash < 0 ? "" : path.substring(0, slash); }
    private String moduleName(String root) { return root.isEmpty() ? "root" : fileName(root); }

    private record Candidate(String path, String hash, byte[] bytes, boolean oversized) { }
    private record ModuleResult(List<RepositoryModuleRecord> modules, Set<String> stack,
                                List<RepositoryDependencyEdgeRecord> dependencies,
                                Set<String> malformedManifests, boolean partial) { }

    private record IgnoreRule(String base, Pattern pattern, boolean negated) {
        static IgnoreRule parse(String base, String raw) {
            if (raw == null) return null;
            String value = raw.strip();
            if (value.isEmpty() || value.startsWith("#")) return null;
            boolean negated = value.startsWith("!");
            if (negated) value = value.substring(1);
            if (value.isEmpty()) return null;
            boolean anchored = value.startsWith("/");
            if (anchored) value = value.substring(1);
            boolean directory = value.endsWith("/");
            if (directory) value = value.substring(0, value.length() - 1);
            String regex = globRegex(value);
            String prefix = base.isEmpty() ? "" : Pattern.quote(base + "/");
            String relativePrefix = anchored || value.contains("/") ? "" : "(?:.*/)?";
            String suffix = directory ? "(?:/.*)?" : "";
            return new IgnoreRule(base, Pattern.compile("^" + prefix + relativePrefix + regex + suffix + "$"), negated);
        }

        boolean matches(String path) {
            return (base.isEmpty() || path.startsWith(base + "/")) && pattern.matcher(path).matches();
        }

        private static String globRegex(String glob) {
            StringBuilder regex = new StringBuilder();
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                if (c == '*') {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') { regex.append(".*"); i++; }
                    else regex.append("[^/]*");
                } else if (c == '?') regex.append("[^/]");
                else {
                    if (".()[]{}+$^|\\".indexOf(c) >= 0) regex.append('\\');
                    regex.append(c);
                }
            }
            return regex.toString();
        }
    }
}
