package com.devlensai.backend.exception;

public class AnalysisNotFoundException extends RuntimeException {

    public AnalysisNotFoundException(Long id) {
        super("Analysis with id " + id + " was not found");
    }
}
