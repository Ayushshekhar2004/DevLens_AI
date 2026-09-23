package com.devlensai.backend.controller;

import com.devlensai.backend.dto.RepositorySnapshotResponse;
import com.devlensai.backend.entity.User;
import com.devlensai.backend.service.RepositoryImportService;
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

    public RepositoryImportController(RepositoryImportService importService) {
        this.importService = importService;
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
}
