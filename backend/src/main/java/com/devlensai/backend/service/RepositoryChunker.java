package com.devlensai.backend.service;

import com.devlensai.backend.config.RepositoryAnalysisProperties;
import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.entity.RepositoryFileRecord;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Component
public class RepositoryChunker {
    public static final String VERSION = "line-window-v1";
    private static final int PROMPT_METADATA_TOKENS = 16;
    private final RepositoryAnalysisProperties properties;

    public RepositoryChunker(RepositoryAnalysisProperties properties) { this.properties = properties; }

    public List<Chunk> chunks(RepositoryFileRecord file) {
        Optional<ProgrammingLanguage> language = language(file.language());
        if (language.isEmpty() || file.safeContent() == null || file.safeContent().isBlank()) return List.of();
        int usable = properties.usableInputTokens() - PROMPT_METADATA_TOKENS;
        int maxChars = Math.max(1, usable * 3); // conservative estimate: at most three UTF-16 chars/token
        String[] lines = file.safeContent().split("\\R", -1);
        List<Chunk> result = new ArrayList<>();
        int line = 0;
        int sequence = 0;
        while (line < lines.length) {
            if (lines[line].length() + 1 > maxChars) {
                String value = lines[line];
                for (int offset = 0; offset < value.length(); offset += maxChars) {
                    String content = value.substring(offset, Math.min(value.length(), offset + maxChars));
                    result.add(chunk(file, language.get(), line + 1, line + 1, sequence++, content));
                }
                line++;
                continue;
            }
            int start = line;
            StringBuilder content = new StringBuilder();
            while (line < lines.length) {
                String candidate = lines[line] + (line + 1 < lines.length ? "\n" : "");
                if (!content.isEmpty() && content.length() + candidate.length() > maxChars) break;
                if (candidate.length() > maxChars) break;
                content.append(candidate);
                line++;
            }
            int endExclusive = Math.max(start + 1, line);
            result.add(chunk(file, language.get(), start + 1, endExclusive, sequence++, content.toString()));
            int overlap = Math.min(properties.overlapLines(), Math.max(0, endExclusive - start - 1));
            line = Math.max(line, start + 1) - overlap;
        }
        return List.copyOf(result);
    }

    public int estimateTokens(String value) { return Math.max(1, (value.length() + 2) / 3 + PROMPT_METADATA_TOKENS); }

    private Chunk chunk(RepositoryFileRecord file, ProgrammingLanguage language, int start, int end,
                        int sequence, String content) {
        String id = sha256(file.relativePath() + "\0" + file.contentHash() + "\0" + start + ":" + end
                + ":" + sequence + "\0" + sha256(content));
        return new Chunk(id, file.relativePath(), file.contentHash(), start, end, sequence,
                language, content, estimateTokens(content));
    }

    private Optional<ProgrammingLanguage> language(String value) {
        return switch (value) {
            case "JAVA" -> Optional.of(ProgrammingLanguage.JAVA);
            case "JAVASCRIPT", "TYPESCRIPT", "JSX", "TSX" -> Optional.of(ProgrammingLanguage.JAVASCRIPT);
            case "PYTHON" -> Optional.of(ProgrammingLanguage.PYTHON);
            case "CPP" -> Optional.of(ProgrammingLanguage.CPP);
            default -> Optional.empty();
        };
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    public record Chunk(String id, String relativePath, String fileHash, int startLine, int endLine,
                        int sequence, ProgrammingLanguage language, String content, int estimatedTokens) { }
}
