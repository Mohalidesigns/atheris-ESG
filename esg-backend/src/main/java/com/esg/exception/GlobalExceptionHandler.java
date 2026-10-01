package com.esg.exception;

import com.esg.model.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps exceptions to HTTP responses. Unknown errors become a generic 500 so internal messages never leak.
 * Full RFC 7807 problem details come with WP-15.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleFieldValidation(FieldValidationException ex) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(ex.getErrors(), false, ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("This record was changed by someone else. Reload and try again."));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You do not have permission to perform this action"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        e -> e.getDefaultMessage() == null ? "Invalid value" : e.getDefaultMessage(), (a, b) -> a));
        ApiResponse<Map<String, String>> resp = new ApiResponse<>(errors, false, "Validation failed");
        return ResponseEntity.badRequest().body(resp);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("Malformed request"));
    }

    /** Errors raised by the database itself (RLS/definer permission checks, period-lock trigger, constraints). */
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabase(org.springframework.dao.DataAccessException ex) {
        java.sql.SQLException sql = null;
        for (Throwable t = ex; t != null && sql == null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException s && s.getSQLState() != null) {
                sql = s;
            }
        }
        String state = sql == null ? "" : sql.getSQLState();
        return switch (state) {
            case "42501" -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You do not have permission to perform this action"));
            case "P0L01" -> ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(firstLine(sql.getMessage())));
            case "P0V01" -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("This record was changed by someone else. Reload and try again."));
            case "23505" -> ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("A record with these values already exists"));
            case "23503" -> ResponseEntity.badRequest().body(ApiResponse.error("A referenced record doesn't exist or is still in use"));
            case "23514" -> ResponseEntity.badRequest().body(ApiResponse.error("A value is outside the allowed range"));
            default -> handleUnexpected(ex);
        };
    }

    private static String firstLine(String message) {
        String text = message == null ? "" : message.replaceFirst("^ERROR: ", "");
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(RuntimeException ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Unexpected error"));
    }
}
