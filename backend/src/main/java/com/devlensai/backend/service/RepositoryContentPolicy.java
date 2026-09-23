package com.devlensai.backend.service;

import com.devlensai.backend.exception.RepositoryImportException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
public class RepositoryContentPolicy {
    private static final Set<String> DENIED_DIRECTORIES = Set.of(
            ".git", ".svn", ".hg", "node_modules", "vendor", "venv", ".venv", "env",
            "build", "dist", "target", ".next", "coverage", ".idea", ".vscode", ".cache",
            "__pycache__", ".pytest_cache", ".mypy_cache", ".gradle"
    );
    private static final Set<String> ARCHIVE_SUFFIXES = Set.of(
            ".zip", ".jar", ".war", ".ear", ".tar", ".tgz", ".gz", ".bz2", ".xz", ".7z", ".rar"
    );
    private static final Set<String> SAFE_SUFFIXES = Set.of(
            ".java", ".kt", ".kts", ".py", ".js", ".jsx", ".ts", ".tsx", ".c", ".h",
            ".cc", ".cpp", ".hpp", ".cs", ".go", ".rs", ".rb", ".php", ".swift", ".scala",
            ".sql", ".html", ".css", ".scss", ".md", ".txt", ".json", ".xml", ".yaml", ".yml",
            ".toml", ".properties", ".gradle"
    );
    private static final Set<String> SAFE_MANIFESTS = Set.of(
            "pom.xml", "package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
            "requirements.txt", "pyproject.toml", "poetry.lock", "cargo.toml", "cargo.lock",
            "go.mod", "go.sum", "build.gradle", "build.gradle.kts", "settings.gradle",
            "settings.gradle.kts", "makefile", "cmakelists.txt", "dockerfile", ".gitignore"
    );

    public Decision decide(Path relativePath) {
        for (Path segment : relativePath) {
            if (DENIED_DIRECTORIES.contains(segment.toString().toLowerCase(Locale.ROOT))) {
                return Decision.skipped();
            }
        }
        String name = relativePath.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (isNestedArchive(lower)) throw new RepositoryImportException("Nested archives are not allowed");
        if (isTemplate(lower)) return Decision.sanitizedTemplate();
        if (isSensitiveName(lower)) return Decision.skipped();
        if (SAFE_MANIFESTS.contains(lower)) return Decision.stored();
        if (SAFE_SUFFIXES.stream().anyMatch(lower::endsWith)) return Decision.stored();
        return Decision.skipped();
    }

    public byte[] validateAndTransform(byte[] content, Decision decision) {
        if (isBinary(content)) throw new RepositoryImportException("Binary content is not allowed");
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.contains("-----BEGIN PRIVATE KEY-----")
                || text.contains("-----BEGIN RSA PRIVATE KEY-----")
                || text.matches("(?s).*AKIA[0-9A-Z]{16}.*")) {
            throw new RepositoryImportException("Likely credential material is not allowed");
        }
        if (!decision.sanitizeTemplate()) return content;
        String sanitized = Arrays.stream(text.split("\\R", -1))
                .map(line -> {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || !line.contains("=")) return line;
                    return line.substring(0, line.indexOf('=') + 1) + "<redacted-template-value>";
                })
                .reduce((left, right) -> left + "\n" + right).orElse("");
        return sanitized.getBytes(StandardCharsets.UTF_8);
    }

    private boolean isNestedArchive(String lower) {
        return ARCHIVE_SUFFIXES.stream().anyMatch(lower::endsWith);
    }

    private boolean isSensitiveName(String lower) {
        return lower.equals(".env") || lower.startsWith(".env.")
                || lower.endsWith(".pem") || lower.endsWith(".key") || lower.endsWith(".p12")
                || lower.endsWith(".pfx") || lower.endsWith(".jks") || lower.endsWith(".keystore")
                || lower.endsWith(".crt") || lower.endsWith(".cer") || lower.endsWith(".der")
                || lower.endsWith(".db") || lower.endsWith(".sqlite") || lower.endsWith(".sqlite3")
                || lower.endsWith(".dump") || lower.endsWith(".bak")
                || lower.contains("credential") || lower.contains("secret");
    }

    private boolean isTemplate(String lower) {
        return lower.endsWith(".env.example") || lower.endsWith(".env.sample") || lower.endsWith(".env.template")
                || lower.contains(".example.") || lower.contains(".sample.") || lower.contains(".template.");
    }

    private boolean isBinary(byte[] content) {
        int checked = Math.min(content.length, 8192);
        for (int index = 0; index < checked; index++) if (content[index] == 0) return true;
        return false;
    }

    public record Decision(boolean store, boolean sanitizeTemplate) {
        static Decision stored() { return new Decision(true, false); }
        static Decision sanitizedTemplate() { return new Decision(true, true); }
        static Decision skipped() { return new Decision(false, false); }
    }
}
