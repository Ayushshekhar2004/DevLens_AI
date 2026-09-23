package com.devlensai.backend.controller;

import com.devlensai.backend.dto.RepositorySnapshotResponse;
import com.devlensai.backend.dto.RepositoryScanResponse;
import com.devlensai.backend.repository.UserRepository;
import com.devlensai.backend.service.JwtService;
import com.devlensai.backend.service.RepositoryImportService;
import com.devlensai.backend.service.RepositoryScanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RepositoryImportController.class)
@AutoConfigureMockMvc(addFilters = false)
class RepositoryImportControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean RepositoryImportService importService;
    @MockitoBean RepositoryScanService scanService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserRepository userRepository;

    @Test
    void importsZipAndReturnsOnlySafeSnapshotMetadata() throws Exception {
        when(importService.importZip(any(), any())).thenReturn(response());
        MockMultipartFile upload = new MockMultipartFile(
                "file", "project.zip", "application/octet-stream", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/repositories/imports").file(upload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.files[0].relativePath").value("src/Main.java"))
                .andExpect(jsonPath("$.files[0].sha256").value("a".repeat(64)))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.providerUrl").doesNotExist());

        verify(importService).importZip(any(), any());
    }

    @Test
    void readsSnapshotThroughOwnerScopedServiceMethod() throws Exception {
        when(importService.findOwned(any(), eq(4L))).thenReturn(response());

        mockMvc.perform(get("/api/repositories/snapshots/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4));

        verify(importService).findOwned(any(), eq(4L));
    }

    @Test
    void startsAndReadsOwnerScopedSnapshotScan() throws Exception {
        RepositoryScanResponse scan = new RepositoryScanResponse(8L, 4L, "lexical-v1", "COMPLETED", "Java",
                Instant.parse("2026-09-23T12:00:00Z"), 0, java.util.Map.of(),
                List.of(), List.of(), List.of(), List.of(), List.of());
        when(scanService.scanOwned(any(), eq(4L))).thenReturn(scan);
        when(scanService.findOwned(any(), eq(4L))).thenReturn(scan);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/repositories/snapshots/4/scan"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parserVersion").value("lexical-v1"));
        mockMvc.perform(get("/api/repositories/snapshots/4/scan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotId").value(4));

        verify(scanService).scanOwned(any(), eq(4L));
        verify(scanService).findOwned(any(), eq(4L));
    }

    private RepositorySnapshotResponse response() {
        return new RepositorySnapshotResponse(4L, "project.zip", 1, 13,
                Instant.parse("2026-09-22T12:00:00Z"),
                List.of(new RepositorySnapshotResponse.FileResponse("src/Main.java", "a".repeat(64), 13)));
    }
}
