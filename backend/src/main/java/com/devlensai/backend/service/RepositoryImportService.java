package com.devlensai.backend.service;

import com.devlensai.backend.config.RepositoryImportProperties;
import com.devlensai.backend.dto.RepositorySnapshotResponse;
import com.devlensai.backend.entity.RepositorySnapshot;
import com.devlensai.backend.entity.RepositorySnapshotFile;
import com.devlensai.backend.entity.User;
import com.devlensai.backend.exception.RepositoryImportException;
import com.devlensai.backend.exception.RepositorySnapshotNotFoundException;
import com.devlensai.backend.repository.RepositorySnapshotRepository;
import org.apache.commons.compress.archivers.zip.AsiExtraField;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class RepositoryImportService {
    private final RepositorySnapshotRepository snapshotRepository;
    private final RepositoryImportProperties properties;
    private final RepositoryContentPolicy contentPolicy;
    private final ObjectMapper objectMapper;

    public RepositoryImportService(RepositorySnapshotRepository snapshotRepository,
                                   RepositoryImportProperties properties,
                                   RepositoryContentPolicy contentPolicy,
                                   ObjectMapper objectMapper) {
        this.snapshotRepository = snapshotRepository;
        this.properties = properties;
        this.contentPolicy = contentPolicy;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RepositorySnapshotResponse importZip(User user, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) throw new RepositoryImportException("ZIP upload is required");
        if (upload.getSize() > properties.maxUploadBytes()) throw limit("upload size");
        String sourceName = safeSourceName(upload.getOriginalFilename(), "repository.zip");
        return createSnapshot(user, sourceName, staging -> {
            try (InputStream raw = upload.getInputStream()) {
                return extractZip(raw, staging);
            }
        });
    }

    @Transactional
    public RepositorySnapshotResponse importFolder(User user, Path requestedFolder) {
        return createSnapshot(user, requestedFolder.getFileName().toString(),
                staging -> snapshotFolder(requestedFolder, staging));
    }

    @Transactional(readOnly = true)
    public RepositorySnapshotResponse findOwned(User user, Long id) {
        return snapshotRepository.findByIdAndUserId(id, user.getId())
                .map(RepositorySnapshotResponse::from)
                .orElseThrow(() -> new RepositorySnapshotNotFoundException(id));
    }

    public void deleteStorage(String storageKey) {
        if (storageKey == null || !storageKey.matches("[0-9a-fA-F-]{36}")) return;
        Path target = properties.storageRoot().resolve(storageKey).normalize();
        if (target.startsWith(properties.storageRoot())) deleteTreeQuietly(target);
    }

    private RepositorySnapshotResponse createSnapshot(User user, String sourceName, ImportOperation operation) {
        String storageKey = UUID.randomUUID().toString();
        Path staging = properties.storageRoot().resolve(".staging-" + storageKey);
        Path completed = properties.storageRoot().resolve(storageKey);
        try {
            Files.createDirectories(properties.storageRoot());
            Files.createDirectory(staging);
            ImportResult result = operation.run(staging);
            if (result.files().isEmpty()) throw new RepositoryImportException("No permitted source files were found");
            writeManifest(staging, result);
            moveCompleted(staging, completed);
            makeReadOnly(completed);
            registerRollbackCleanup(completed);
            RepositorySnapshot snapshot = snapshotRepository.save(new RepositorySnapshot(
                    user, storageKey, sourceName, result.totalBytes(), result.files()));
            return RepositorySnapshotResponse.from(snapshot);
        } catch (RepositoryImportException exception) {
            deleteTreeQuietly(staging);
            deleteTreeQuietly(completed);
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            deleteTreeQuietly(staging);
            deleteTreeQuietly(completed);
            throw new RepositoryImportException("Repository import was cancelled", exception);
        } catch (IOException exception) {
            deleteTreeQuietly(staging);
            deleteTreeQuietly(completed);
            throw new RepositoryImportException("Repository import failed safely", exception);
        } catch (RuntimeException exception) {
            deleteTreeQuietly(staging);
            deleteTreeQuietly(completed);
            throw exception;
        }
    }

    private ImportResult extractZip(InputStream source, Path staging) throws IOException, InterruptedException {
        long started = System.nanoTime();
        CountingLimitedInputStream counted = new CountingLimitedInputStream(source, properties.maxUploadBytes());
        List<RepositorySnapshotFile> files = new ArrayList<>();
        Set<String> exactPaths = new HashSet<>();
        Set<String> foldedPaths = new HashSet<>();
        long expanded = 0;
        int encounteredFiles = 0;
        try (ZipArchiveInputStream zip = new ZipArchiveInputStream(counted)) {
            ZipArchiveEntry entry;
            while ((entry = zip.getNextZipEntry()) != null) {
                checkElapsed(started);
                validateZipMetadata(zip, entry);
                if (entry.isDirectory()) continue;
                encounteredFiles++;
                if (encounteredFiles > properties.maxFiles()) throw limit("file count");
                Path relative = normalizedRelativePath(entry.getName());
                registerUniquePath(relative, exactPaths, foldedPaths);
                RepositoryContentPolicy.Decision decision = contentPolicy.decide(relative);
                ReadResult read = readBounded(zip, entry.getSize(), expanded, started, decision.store());
                expanded += read.expandedBytes();
                if (!decision.store()) continue;
                byte[] stored = contentPolicy.validateAndTransform(read.content(), decision);
                files.add(writeFile(staging, relative, stored));
            }
        }
        return new ImportResult(List.copyOf(files), files.stream().mapToLong(RepositorySnapshotFile::getSizeBytes).sum());
    }

    private ImportResult snapshotFolder(Path requested, Path staging) throws IOException, InterruptedException {
        long started = System.nanoTime();
        Path allowedRoot = properties.allowedFolderRoot().toRealPath();
        if (Files.isSymbolicLink(requested)) throw new RepositoryImportException("Folder symlinks are not allowed");
        Path sourceRoot = requested.toRealPath();
        if (!sourceRoot.startsWith(allowedRoot) || sourceRoot.equals(allowedRoot)) {
            throw new RepositoryImportException("Folder is outside the configured allowed root");
        }
        List<RepositorySnapshotFile> files = new ArrayList<>();
        Set<String> exactPaths = new HashSet<>();
        Set<String> foldedPaths = new HashSet<>();
        MutableLimits limits = new MutableLimits();
        List<Path> discovered;
        try (var walk = Files.walk(sourceRoot)) {
            discovered = walk.sorted((left, right) -> sourceRoot.relativize(left).toString()
                    .compareTo(sourceRoot.relativize(right).toString())).toList();
        }
        for (Path file : discovered) {
            checkInterrupted();
            checkElapsed(started);
            if (file.equals(sourceRoot)) continue;
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attrs.isSymbolicLink() || Files.isSymbolicLink(file)) {
                throw new RepositoryImportException("Folder symlinks are not allowed");
            }
            if (attrs.isDirectory()) continue;
            if (!attrs.isRegularFile()) throw new RepositoryImportException("Special files are not allowed");
            limits.files++;
            if (limits.files > properties.maxFiles()) throw limit("file count");
            Path relative = normalizedRelativePath(sourceRoot.relativize(file).toString());
            registerUniquePath(relative, exactPaths, foldedPaths);
            RepositoryContentPolicy.Decision decision = contentPolicy.decide(relative);
            long sizeBefore = attrs.size();
            FileTime modifiedBefore = attrs.lastModifiedTime();
            if (sizeBefore > properties.maxFileBytes()) throw limit("individual file size");
            if (limits.expanded + sizeBefore > properties.maxExpandedBytes()) throw limit("expanded bytes");
            limits.expanded += sizeBefore;
            if (!decision.store()) continue;
            byte[] content = Files.readAllBytes(file);
            BasicFileAttributes after = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (after.size() != sizeBefore || !after.lastModifiedTime().equals(modifiedBefore)) {
                throw new RepositoryImportException("Source changed while snapshotting");
            }
            byte[] stored = contentPolicy.validateAndTransform(content, decision);
            files.add(writeFile(staging, relative, stored));
        }
        return new ImportResult(List.copyOf(files), files.stream().mapToLong(RepositorySnapshotFile::getSizeBytes).sum());
    }

    private void validateZipMetadata(ZipArchiveInputStream zip, ZipArchiveEntry entry) {
        if (!zip.canReadEntryData(entry) || entry.getGeneralPurposeBit().usesEncryption()) {
            throw new RepositoryImportException("Encrypted or unsupported ZIP entries are not allowed");
        }
        AsiExtraField asi = null;
        for (var extraField : entry.getExtraFields()) {
            if (extraField instanceof AsiExtraField candidate) asi = candidate;
        }
        int type = entry.getUnixMode() & UnixStat.FILE_TYPE_FLAG;
        boolean specialType = type != 0 && type != UnixStat.FILE_FLAG && type != UnixStat.DIR_FLAG;
        if (entry.isUnixSymlink() || (asi != null && asi.isLink()) || specialType) {
            throw new RepositoryImportException("Links and special ZIP entries are not allowed");
        }
    }

    private ReadResult readBounded(ZipArchiveInputStream input, long declaredSize, long expandedBefore,
                                   long started, boolean capture)
            throws IOException, InterruptedException {
        if (declaredSize > properties.maxFileBytes()) throw limit("individual file size");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long fileBytes = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            checkElapsed(started);
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            fileBytes += read;
            long expanded = expandedBefore + fileBytes;
            if (fileBytes > properties.maxFileBytes()) throw limit("individual file size");
            if (expanded > properties.maxExpandedBytes()) throw limit("expanded bytes");
            if (fileBytes > 1024 && fileBytes / (double) Math.max(1, input.getCompressedCount())
                    > properties.maxDecompressionRatio()) throw limit("decompression ratio");
            if (capture) output.write(buffer, 0, read);
        }
        return new ReadResult(output.toByteArray(), fileBytes);
    }

    private RepositorySnapshotFile writeFile(Path staging, Path relative, byte[] content) throws IOException {
        Path destination = staging.resolve("content").resolve(relative).normalize();
        Path contentRoot = staging.resolve("content").normalize();
        if (!destination.startsWith(contentRoot)) throw new RepositoryImportException("Path escapes snapshot root");
        Files.createDirectories(destination.getParent());
        Files.write(destination, content);
        return new RepositorySnapshotFile(relative.toString().replace('\\', '/'), sha256(content), content.length);
    }

    private Path normalizedRelativePath(String raw) {
        if (raw == null || raw.isBlank() || raw.indexOf('\0') >= 0 || raw.contains("\\")
                || raw.startsWith("/") || raw.startsWith("//") || raw.matches("^[A-Za-z]:.*")) {
            throw new RepositoryImportException("ZIP contains an unsafe path");
        }
        String unicode = Normalizer.normalize(raw, Normalizer.Form.NFC);
        Path normalized = Path.of(unicode).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..") || normalized.getNameCount() > properties.maxPathDepth()) {
            throw new RepositoryImportException("ZIP contains an unsafe or overly deep path");
        }
        return normalized;
    }

    private void registerUniquePath(Path relative, Set<String> exact, Set<String> folded) {
        String value = relative.toString().replace('\\', '/');
        if (!exact.add(value) || !folded.add(value.toLowerCase(Locale.ROOT))) {
            throw new RepositoryImportException("ZIP contains duplicate or case-colliding paths");
        }
    }

    private void writeManifest(Path staging, ImportResult result) throws IOException {
        List<ManifestFile> manifest = result.files().stream().map(file ->
                new ManifestFile(file.getRelativePath(), file.getSha256(), file.getSizeBytes())).toList();
        objectMapper.writeValue(staging.resolve("manifest.json").toFile(),
                new Manifest(Instant.now(), result.totalBytes(), manifest));
    }

    private void moveCompleted(Path staging, Path completed) throws IOException {
        try {
            Files.move(staging, completed, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(staging, completed);
        }
    }

    private void registerRollbackCleanup(Path completed) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) deleteTreeQuietly(completed);
            }
        });
    }

    private void makeReadOnly(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                file.toFile().setWritable(false, false);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteTreeQuietly(Path root) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    file.toFile().setWritable(true, false);
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                    dir.toFile().setWritable(true, false);
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup; original import exception remains safe and does not expose content.
        }
    }

    private void checkElapsed(long started) {
        if (System.nanoTime() - started > properties.maxElapsed().toNanos()) throw limit("elapsed time");
    }

    private void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new RepositoryImportException("Repository import was cancelled");
    }

    private RepositoryImportException limit(String limit) {
        return new RepositoryImportException("Repository import exceeded the configured " + limit + " limit");
    }

    private String safeSourceName(String provided, String fallback) {
        if (provided == null || provided.isBlank()) return fallback;
        String normalized = provided.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        return name.isEmpty() ? fallback : name.substring(0, Math.min(name.length(), 255));
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private interface ImportOperation { ImportResult run(Path staging) throws IOException, InterruptedException; }
    private record ImportResult(List<RepositorySnapshotFile> files, long totalBytes) { }
    private record ReadResult(byte[] content, long expandedBytes) { }
    private record Manifest(Instant createdAt, long totalBytes, List<ManifestFile> files) { }
    private record ManifestFile(String relativePath, String sha256, long sizeBytes) { }
    private static final class MutableLimits { private int files; private long expanded; }

    private static final class CountingLimitedInputStream extends FilterInputStream {
        private final long maximum;
        private long count;
        private CountingLimitedInputStream(InputStream input, long maximum) { super(input); this.maximum = maximum; }
        @Override public int read() throws IOException {
            int value = super.read();
            if (value >= 0) increment(1);
            return value;
        }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) increment(read);
            return read;
        }
        private void increment(int amount) { count += amount; if (count > maximum) throw limitStatic("upload size"); }
        private long count() { return count; }
        private static RepositoryImportException limitStatic(String limit) {
            return new RepositoryImportException("Repository import exceeded the configured " + limit + " limit");
        }
    }
}
