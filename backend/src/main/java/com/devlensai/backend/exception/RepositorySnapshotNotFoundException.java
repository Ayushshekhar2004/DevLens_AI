package com.devlensai.backend.exception;

public class RepositorySnapshotNotFoundException extends RuntimeException {
    public RepositorySnapshotNotFoundException(Long id) {
        super("Repository snapshot with id " + id + " was not found");
    }
}
