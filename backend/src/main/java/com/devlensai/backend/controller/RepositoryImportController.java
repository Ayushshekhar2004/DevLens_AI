package com.devlensai.backend.controller;

import com.devlensai.backend.dto.RepositorySnapshotResponse;
import com.devlensai.backend.dto.RepositoryScanResponse;
import com.devlensai.backend.entity.User;
import com.devlensai.backend.service.RepositoryImportService;
import com.devlensai.backend.service.RepositoryScanService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryImportController {
    private final RepositoryImportService importService;
    private final RepositoryScanService scanService;

    public RepositoryImportController(RepositoryImportService importService, RepositoryScanService scanService) {
        this.importService = importService;
        this.scanService = scanService;
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
}
