package com.devlensai.backend.exception;

import com.devlensai.backend.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OllamaSelectionException.class)
    public ResponseEntity<ApiErrorResponse> handleOllamaSelection(OllamaSelectionException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(AiProviderTimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleOllamaTimeout() {
        return error(HttpStatus.GATEWAY_TIMEOUT, "Ollama connection timed out", Map.of());
    }

    @ExceptionHandler(AiProviderUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleOllamaUnavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Ollama is unavailable", Map.of());
    }

    @ExceptionHandler({AiProviderApiException.class, AiProviderMalformedResponseException.class})
    public ResponseEntity<ApiErrorResponse> handleOllamaApiFailure() {
        return error(HttpStatus.BAD_GATEWAY, "Ollama connection failed or returned invalid data", Map.of());
    }

    @ExceptionHandler(AnalysisNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(AnalysisNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(RepositorySnapshotNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleRepositorySnapshotNotFound(
            RepositorySnapshotNotFoundException exception
    ) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(RepositoryImportException.class)
    public ResponseEntity<ApiErrorResponse> handleRepositoryImport(RepositoryImportException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(RepositoryScanException.class)
    public ResponseEntity<ApiErrorResponse> handleRepositoryScan(RepositoryScanException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRepositoryUploadTooLarge() {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "Repository upload exceeds the configured size limit", Map.of());
    }

    @ExceptionHandler(AnalysisReviewFailedException.class)
    public ResponseEntity<ApiErrorResponse> handleReviewFailure(AnalysisReviewFailedException exception) {
        return error(exception.getResponseStatus(), exception.getMessage(), Map.of());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(EmailAlreadyRegisteredException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(InvalidHistoryQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidHistoryQuery(InvalidHistoryQueryException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()));

        return error(HttpStatus.BAD_REQUEST, "Request validation failed", fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest() {
        return error(
                HttpStatus.BAD_REQUEST,
                "Request body is malformed or contains an unsupported value",
                Map.of()
        );
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String message,
            Map<String, String> fieldErrors
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                fieldErrors
        );
        return ResponseEntity.status(status).body(response);
    }
}
