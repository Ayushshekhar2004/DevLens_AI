package com.devlensai.backend.controller;

import com.devlensai.backend.dto.RepositorySnapshotResponse;
import com.devlensai.backend.dto.RepositoryScanResponse;
import com.devlensai.backend.dto.RepositoryImportLifecycleResponse;
import com.devlensai.backend.dto.RepositoryInventoryResponse;
import com.devlensai.backend.dto.RepositoryJobResponse;
import com.devlensai.backend.entity.User;
import com.devlensai.backend.service.RepositoryImportService;
import com.devlensai.backend.service.RepositoryScanService;
import com.devlensai.backend.service.RepositoryLifecycleService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryImportController {
    private final RepositoryImportService importService;
    private final RepositoryScanService scanService;
    private final RepositoryLifecycleService lifecycleService;

    public RepositoryImportController(RepositoryImportService importService, RepositoryScanService scanService,
                                      RepositoryLifecycleService lifecycleService) {
        this.importService = importService;
        this.scanService = scanService;
        this.lifecycleService = lifecycleService;
    }

    @PostMapping(path = "/import-jobs", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public RepositoryImportLifecycleResponse importAndScan(
            @AuthenticationPrincipal User user, @RequestPart("file") MultipartFile file) {
        return lifecycleService.importAndQueue(user, file);
    }

    @PostMapping(path = "/imports", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public RepositorySnapshotResponse importZip(
            @AuthenticationPrincipal User user,
            @RequestPart("file") MultipartFile file
    ) {
        return importService.importZip(user, file);
    }

    @GetMapping("/snapshots/{id}")
    public RepositorySnapshotResponse findSnapshot(
            @AuthenticationPrincipal User user,
            @PathVariable Long id
    ) {
        return importService.findOwned(user, id);
    }

    @PostMapping("/snapshots/{id}/scan")
    @ResponseStatus(HttpStatus.CREATED)
    public RepositoryScanResponse scanSnapshot(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return scanService.scanOwned(user, id);
    }

    @GetMapping("/snapshots/{id}/scan")
    public RepositoryScanResponse findScan(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return scanService.findOwned(user, id);
    }

    @PostMapping("/snapshots/{id}/scan-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RepositoryJobResponse queueScan(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return lifecycleService.queueScan(user, id);
    }

    @GetMapping("/jobs/{id}")
    public RepositoryJobResponse job(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return lifecycleService.job(user, id);
    }

    @PostMapping("/jobs/{id}/cancel")
    public RepositoryJobResponse cancel(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return lifecycleService.cancel(user, id);
    }

    @GetMapping("/inventory")
    public RepositoryInventoryResponse.Page<RepositoryInventoryResponse.Summary> inventory(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return lifecycleService.list(user, page, size);
    }

    @GetMapping("/snapshots/{id}/inventory")
    public RepositoryInventoryResponse.Detail inventoryDetail(
            @AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return lifecycleService.detail(user, id, page, size);
    }

    @DeleteMapping("/snapshots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        lifecycleService.delete(user, id);
    }
}
