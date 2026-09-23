package com.devlensai.backend.controller;

import com.devlensai.backend.repository.RepositoryJobRepository;
import com.devlensai.backend.repository.RepositoryScanRepository;
import com.devlensai.backend.repository.RepositorySnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=repository-lifecycle-test-secret-at-least-32-bytes",
        "app.repository-import.max-upload-bytes=20000", "app.repository-import.max-expanded-bytes=100000",
        "app.repository-import.max-file-bytes=10000", "app.repository-import.max-files=100",
        "app.repository-lifecycle.worker-count=1", "app.repository-lifecycle.queue-capacity=4",
        "app.repository-lifecycle.retention-days=0"
})
@AutoConfigureMockMvc
class RepositoryLifecycleApiTest {
    private static final Path STORAGE = createDirectory("devlens-lifecycle-storage-");
    private static final Path ALLOWED = createDirectory("devlens-lifecycle-allowed-");

    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry registry) {
        registry.add("app.repository-import.storage-root", STORAGE::toString);
        registry.add("app.repository-import.allowed-folder-root", ALLOWED::toString);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired RepositorySnapshotRepository snapshots;
    @Autowired RepositoryScanRepository scans;
    @Autowired RepositoryJobRepository jobs;
    @Autowired @Qualifier("repositoryTaskExecutor") ThreadPoolTaskExecutor repositoryExecutor;

    @Test
    void authenticatedLifecycleImportsPollsInventoriesIsolatesAndDeletes() throws Exception {
        String owner = registerAndLogin("owner@fixture.test");
        String other = registerAndLogin("other@fixture.test");

        mockMvc.perform(multipart("/api/repositories/import-jobs").file(fixtureZip()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/repositories/import-jobs").file(
                        new MockMultipartFile("file", "invalid.zip", "application/zip", "not-a-zip".getBytes()))
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/repositories/import-jobs").file(
                        new MockMultipartFile("file", "large.zip", "application/zip", new byte[20_001]))
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest());

        CountDownLatch releaseWorker = new CountDownLatch(1);
        var blocker = repositoryExecutor.submit(() -> {
            try { releaseWorker.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        });
        String cancellable = mockMvc.perform(multipart("/api/repositories/import-jobs").file(fixtureZip())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode cancellableJson = mapper.readTree(cancellable);
        long cancelledSnapshot = cancellableJson.path("snapshotId").longValue();
        long cancelledJob = cancellableJson.path("scanJob").path("id").longValue();
        mockMvc.perform(post("/api/repositories/jobs/{id}/cancel", cancelledJob)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("CANCELLED"));
        releaseWorker.countDown(); blocker.get(2, TimeUnit.SECONDS);
        mockMvc.perform(delete("/api/repositories/snapshots/{id}", cancelledSnapshot)
                        .header("Authorization", bearer(owner))).andExpect(status().isNoContent());

        String imported = mockMvc.perform(multipart("/api/repositories/import-jobs").file(fixtureZip())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode importJson = mapper.readTree(imported);
        long snapshotId = importJson.path("snapshotId").longValue();
        long scanJobId = importJson.path("scanJob").path("id").longValue();
        assertThat(snapshotId).isPositive();

        JsonNode job = poll(owner, scanJobId);
        assertThat(job.path("status").stringValue()).isEqualTo("COMPLETED");
        mockMvc.perform(get("/api/repositories/jobs/{id}", scanJobId)
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/repositories/jobs/{id}/cancel", scanJobId)
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());

        String duplicateScan = mockMvc.perform(post("/api/repositories/snapshots/{id}/scan-jobs", snapshotId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(duplicateScan).path("id").longValue()).isEqualTo(scanJobId);

        String secondImport = mockMvc.perform(multipart("/api/repositories/import-jobs").file(fixtureZip())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode secondJson = mapper.readTree(secondImport);
        long secondSnapshotId = secondJson.path("snapshotId").longValue();
        assertThat(poll(owner, secondJson.path("scanJob").path("id").longValue()).path("status").stringValue())
                .isEqualTo("COMPLETED");

        String inventory = mockMvc.perform(get("/api/repositories/inventory?page=0&size=1")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode summary = mapper.readTree(inventory).path("content").path(0);
        assertThat(mapper.readTree(inventory).path("totalElements").longValue()).isEqualTo(2);
        assertThat(mapper.readTree(inventory).path("totalPages").intValue()).isEqualTo(2);
        assertThat(summary.path("detectedStack").stringValue()).contains("Java", "React");
        assertThat(summary.path("includedCount").intValue()).isPositive();
        assertThat(summary.path("skippedCount").intValue()).isGreaterThanOrEqualTo(2);
        assertThat(summary.path("skipReasons").toString()).doesNotContain("fixture-secret", "must-not-persist");
        mockMvc.perform(get("/api/repositories/inventory?page=0&size=51")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest());

        String detail = mockMvc.perform(get("/api/repositories/snapshots/{id}/inventory?page=0&size=2", snapshotId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode detailJson = mapper.readTree(detail);
        assertThat(detailJson.path("modules").size()).isEqualTo(2);
        assertThat(detailJson.path("files").path("size").intValue()).isEqualTo(2);
        assertThat(detail).doesNotContain("credentials.properties", "ignored.ts", "generated.min.js",
                "fixture-secret", "must-not-persist-as-scan-context", "safeContent", STORAGE.toString());

        mockMvc.perform(get("/api/repositories/snapshots/{id}/inventory", snapshotId)
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/repositories/snapshots/{id}", snapshotId)
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/repositories/snapshots/{id}", snapshotId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/repositories/snapshots/{id}/inventory", snapshotId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/repositories/snapshots/{id}", secondSnapshotId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        assertThat(snapshots.count()).isZero();
        assertThat(scans.count()).isZero();
        assertThat(jobs.countBySnapshotId(snapshotId)).isZero();
        assertThat(jobs.countBySnapshotId(secondSnapshotId)).isZero();
        try (var content = Files.list(STORAGE)) { assertThat(content).isEmpty(); }
    }

    private JsonNode poll(String token, long id) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            String response = mockMvc.perform(get("/api/repositories/jobs/{id}", id)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode json = mapper.readTree(response);
            if (!json.path("status").stringValue().matches("QUEUED|RUNNING")) return json;
            Thread.sleep(20);
        }
        throw new AssertionError("Synthetic scan did not reach a terminal state");
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"name\":\"Fixture User\",\"email\":\"" + email + "\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isCreated());
        String login = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(login).path("token").stringValue();
    }

    private MockMultipartFile fixtureZip() throws Exception {
        Path root = Path.of("src/test/resources/repository-fixtures/mixed-project");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes); var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList()) {
                zip.putNextEntry(new ZipEntry(root.relativize(file).toString().replace('\\', '/')));
                zip.write(Files.readAllBytes(file));
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", "mixed-project.zip", "application/zip", bytes.toByteArray());
    }

    private String bearer(String token) { return "Bearer " + token; }
    private static Path createDirectory(String prefix) {
        try { return Files.createTempDirectory(prefix); }
        catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
}
