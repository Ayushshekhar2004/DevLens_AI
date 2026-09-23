package com.devlensai.backend.service;

import com.devlensai.backend.config.RepositoryImportProperties;
import com.devlensai.backend.entity.RepositorySnapshot;
import com.devlensai.backend.entity.User;
import com.devlensai.backend.exception.RepositoryImportException;
import com.devlensai.backend.exception.RepositorySnapshotNotFoundException;
import com.devlensai.backend.repository.RepositorySnapshotRepository;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.AsiExtraField;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryImportServiceTest {
    @TempDir Path temporary;
    private Path storage;
    private Path allowed;
    private RepositorySnapshotRepository repository;
    private AtomicReference<RepositorySnapshot> saved;
    private User owner;

    @BeforeEach
    void setUp() throws IOException {
        storage = temporary.resolve("private-storage");
        allowed = Files.createDirectory(temporary.resolve("allowed"));
        repository = mock(RepositorySnapshotRepository.class);
        saved = new AtomicReference<>();
        when(repository.save(any(RepositorySnapshot.class))).thenAnswer(invocation -> {
            RepositorySnapshot snapshot = invocation.getArgument(0);
            saved.set(snapshot);
            return snapshot;
        });
        owner = mock(User.class);
        when(owner.getId()).thenReturn(11L);
    }

    @Test
    void importsValidZipDeterministicallyAndFiltersSensitiveContent() throws Exception {
        byte[] zip = zip(
                entry("src/Main.java", "class Main {}"),
                entry("README.md", "safe"),
                entry(".env", "TOKEN=real-secret"),
                entry("config.env.example", "TOKEN=placeholder\nPORT=8080"),
                entry("node_modules/pkg/index.js", "ignored")
        );

        var response = service(limits(10_000, 20_000, 5_000, 20, 100))
                .importZip(owner, upload(zip));

        assertThat(response.files()).extracting(file -> file.relativePath())
                .containsExactly("src/Main.java", "README.md", "config.env.example");
        assertThat(response.files()).allSatisfy(file -> assertThat(file.sha256()).hasSize(64));
        Path snapshotRoot = onlyCompletedSnapshot();
        assertThat(Files.readString(snapshotRoot.resolve("content/config.env.example")))
                .isEqualTo("TOKEN=<redacted-template-value>\nPORT=<redacted-template-value>");
        assertThat(snapshotRoot.resolve("content/.env")).doesNotExist();
        assertThat(snapshotRoot.resolve("manifest.json")).exists();
    }

    @Test
    void rejectsTraversalAbsoluteDriveAndCaseCollisions() throws Exception {
        for (String unsafe : List.of("../escape.java", "/absolute.java", "C:/drive.java", "\\\\server\\share.java")) {
            assertThatThrownBy(() -> service(limits(10_000, 20_000, 5_000, 20, 100))
                    .importZip(owner, upload(zip(entry(unsafe, "class X {}")))))
                    .isInstanceOf(RepositoryImportException.class);
            assertStorageClean();
        }

        assertThatThrownBy(() -> service(limits(10_000, 20_000, 5_000, 20, 100)).importZip(owner,
                upload(zip(entry("src/Main.java", "a"), entry("SRC/main.java", "b")))))
                .isInstanceOf(RepositoryImportException.class)
                .hasMessageContaining("case-colliding");
        assertStorageClean();
    }

    @Test
    void rejectsZipSymlinkAndNestedArchive() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(bytes)) {
            ZipArchiveEntry symlink = new ZipArchiveEntry("linked.java");
            symlink.setUnixMode(UnixStat.LINK_FLAG | 0777);
            AsiExtraField linkMetadata = new AsiExtraField();
            linkMetadata.setLinkedFile("../outside.java");
            linkMetadata.setMode(UnixStat.LINK_FLAG | 0777);
            symlink.addExtraField(linkMetadata);
            output.putArchiveEntry(symlink);
            output.write("../outside.java".getBytes(StandardCharsets.UTF_8));
            output.closeArchiveEntry();
        }
        assertThatThrownBy(() -> service(limits(10_000, 20_000, 5_000, 20, 100))
                .importZip(owner, upload(bytes.toByteArray())))
                .isInstanceOf(RepositoryImportException.class)
                .hasMessageContaining("Links");
        assertStorageClean();

        assertThatThrownBy(() -> service(limits(10_000, 20_000, 5_000, 20, 100))
                .importZip(owner, upload(zip(entry("nested.zip", "not recursively extracted")))))
                .isInstanceOf(RepositoryImportException.class)
                .hasMessageContaining("Nested archives");
        assertStorageClean();
    }

    @Test
    void stopsOnExpandedBytesAndExcessiveFileCount() throws Exception {
        assertThatThrownBy(() -> service(limits(20_000, 500, 500, 20, 100))
                .importZip(owner, upload(zip(entry("large.java", "x".repeat(600))))))
                .isInstanceOf(RepositoryImportException.class)
                .hasMessageContaining("file size");
        assertStorageClean();

        assertThatThrownBy(() -> service(limits(20_000, 20_000, 5_000, 2, 100)).importZip(owner,
                upload(zip(entry("a.java", "a"), entry("b.java", "b"), entry("c.java", "c")))))
                .isInstanceOf(RepositoryImportException.class)
                .hasMessageContaining("file count");
        assertStorageClean();
    }

    @Test
    void enforcesStreamedUploadLimitEvenWhenReportedSizeIsFalse() throws Exception {
        byte[] validZip = zip(entry("Main.java", "class Main { String value = \"" + "x".repeat(500) + "\"; }"));
        MultipartFile misleading = mock(MultipartFile.class);
        when(misleading.isEmpty()).thenReturn(false);
        when(misleading.getSize()).thenReturn(1L);
        when(misleading.getOriginalFilename()).thenReturn("project.zip");
        when(misleading.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(validZip));

        assertThatThrownBy(() -> service(limits(100, 20_000, 5_000, 20, 100))
                .importZip(owner, misleading))
                .isInstanceOf(RepositoryImportException.class)
                .hasMessageContaining("upload size");
        verify(repository, never()).save(any());
        assertStorageClean();
    }

    @Test
    void stopsHighlyCompressedEntryWhileStreaming() throws Exception {
        byte[] compressed = zip(entry("Repeated.java", "A".repeat(3_000)));

        assertThatThrownBy(() -> service(limits(20_000, 20_000, 5_000, 20, 2))
                .importZip(owner, upload(compressed)))
                .isInstanceOf(RepositoryImportException.class)
                .hasMessageContaining("decompression ratio");
        assertStorageClean();
    }

    @Test
    void storesMetadataButNeverExecutesImportedHooksOrScripts() throws Exception {
        Path marker = temporary.resolve("must-not-exist");
        String packageJson = "{\"scripts\":{\"install\":\"touch " + marker + "\"}}";
        byte[] zip = zip(
                entry("package.json", packageJson),
                entry("install.sh", "touch " + marker),
                entry("src/Main.java", "class Main {}")
        );

        var response = service(limits(20_000, 20_000, 5_000, 20, 100)).importZip(owner, upload(zip));

        assertThat(response.files()).extracting(file -> file.relativePath())
                .containsExactly("package.json", "src/Main.java");
        assertThat(marker).doesNotExist();
    }

    @Test
    void importsTrustedFolderWithoutFollowingLinksAndSanitizesTemplates() throws Exception {
        Path source = Files.createDirectory(allowed.resolve("project"));
        Files.createDirectories(source.resolve("src"));
        Files.writeString(source.resolve("src/Main.java"), "class Main {}");
        Files.writeString(source.resolve(".env.example"), "PASSWORD=placeholder");

        var response = service(limits(20_000, 20_000, 5_000, 20, 100)).importFolder(owner, source);

        assertThat(response.files()).extracting(file -> file.relativePath())
                .containsExactly(".env.example", "src/Main.java");
        assertThat(Files.readString(onlyCompletedSnapshot().resolve("content/.env.example")))
                .isEqualTo("PASSWORD=<redacted-template-value>");
    }

    @Test
    void rejectsFolderSymlinkAndAllowedRootEscape() throws Exception {
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Files.writeString(outside.resolve("Main.java"), "class Main {}");
        Path linked = allowed.resolve("linked");
        Files.createSymbolicLink(linked, outside);

        assertThatThrownBy(() -> service(limits(20_000, 20_000, 5_000, 20, 100))
                .importFolder(owner, linked))
                .isInstanceOf(RepositoryImportException.class)
                .hasMessageContaining("symlink");
        assertThatThrownBy(() -> service(limits(20_000, 20_000, 5_000, 20, 100))
                .importFolder(owner, outside))
                .isInstanceOf(RepositoryImportException.class)
                .hasMessageContaining("allowed root");
        assertStorageClean();
    }

    @Test
    void cleansInterruptedImportAndDoesNotPersist() throws Exception {
        byte[] validZip = zip(entry("src/Main.java", "class Main {}"));
        MultipartFile interrupted = mock(MultipartFile.class);
        when(interrupted.isEmpty()).thenReturn(false);
        when(interrupted.getSize()).thenReturn((long) validZip.length);
        when(interrupted.getOriginalFilename()).thenReturn("project.zip");
        when(interrupted.getInputStream()).thenReturn(new InputStream() {
            int index;
            @Override public int read() throws IOException {
                if (index >= Math.min(validZip.length, 24)) throw new IOException("synthetic interruption");
                return validZip[index++] & 0xff;
            }
        });

        assertThatThrownBy(() -> service(limits(20_000, 20_000, 5_000, 20, 100))
                .importZip(owner, interrupted))
                .isInstanceOf(RepositoryImportException.class)
                .hasMessage("Repository import failed safely")
                .hasMessageNotContaining("synthetic interruption");
        verify(repository, never()).save(any());
        assertStorageClean();
    }

    @Test
    void scopesSnapshotLookupToOwner() {
        User other = mock(User.class);
        when(other.getId()).thenReturn(22L);
        when(repository.findByIdAndUserId(7L, 22L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(limits(20_000, 20_000, 5_000, 20, 100))
                .findOwned(other, 7L))
                .isInstanceOf(RepositorySnapshotNotFoundException.class);

        verify(repository).findByIdAndUserId(7L, 22L);
    }

    private RepositoryImportService service(RepositoryImportProperties properties) {
        return new RepositoryImportService(repository, properties, new RepositoryContentPolicy(), new ObjectMapper());
    }

    private RepositoryImportProperties limits(long upload, long expanded, long file,
                                              int count, double ratio) {
        return new RepositoryImportProperties(storage.toString(), allowed.toString(), upload, expanded,
                file, count, 12, ratio, 10);
    }

    private MockMultipartFile upload(byte[] bytes) {
        return new MockMultipartFile("file", "project.zip", "application/zip", bytes);
    }

    private byte[] zip(ZipFixture... fixtures) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(bytes)) {
            for (ZipFixture fixture : fixtures) {
                output.putArchiveEntry(new ZipArchiveEntry(fixture.name()));
                output.write(fixture.content().getBytes(StandardCharsets.UTF_8));
                output.closeArchiveEntry();
            }
        }
        return bytes.toByteArray();
    }

    private ZipFixture entry(String name, String content) { return new ZipFixture(name, content); }

    private Path onlyCompletedSnapshot() throws IOException {
        try (var entries = Files.list(storage)) {
            List<Path> paths = entries.filter(path -> !path.getFileName().toString().startsWith(".staging-")).toList();
            assertThat(paths).hasSize(1);
            return paths.getFirst();
        }
    }

    private void assertStorageClean() throws IOException {
        if (!Files.exists(storage)) return;
        try (var entries = Files.list(storage)) { assertThat(entries).isEmpty(); }
    }

    private record ZipFixture(String name, String content) { }
}
