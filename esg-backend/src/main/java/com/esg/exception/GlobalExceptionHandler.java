package com.esg.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * Maps exceptions to RFC 7807 problem details (TRD §12.3) with ESG-xxx error codes. Unknown errors become a
 * generic 500 so internal messages never leak; they're logged with the request path.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ProblemDetail> handleFieldValidation(FieldValidationException ex, HttpServletRequest req) {
        return Problems.response(HttpStatus.BAD_REQUEST, ex.getCode(), "One or more fields are invalid", req.getRequestURI(), ex.getErrors());
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest req) {
        return Problems.response(ex.getStatus(), ex.getCode(), ex.getMessage(), req.getRequestURI(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError e : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(e.getField(), e.getDefaultMessage() == null ? "Invalid value" : e.getDefaultMessage());
        }
        return Problems.response(HttpStatus.BAD_REQUEST, "ESG-400-VALIDATION", "One or more fields are invalid", req.getRequestURI(), errors);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ProblemDetail> handleAccessDenied(RuntimeException ex, HttpServletRequest req) {
        return Problems.response(HttpStatus.FORBIDDEN, "ESG-403", "You do not have permission to perform this action", req.getRequestURI(), null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(RuntimeException ex, HttpServletRequest req) {
        return Problems.response(HttpStatus.CONFLICT, "ESG-409-VERSION", "This record was changed by someone else. Reload and try again.",
                req.getRequestURI(), null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MultipartException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(Exception ex, HttpServletRequest req) {
        if (ex instanceof MaxUploadSizeExceededException) {
            return Problems.response(HttpStatus.PAYLOAD_TOO_LARGE, "ESG-413", "The file is larger than the upload limit", req.getRequestURI(), null);
        }
        // Name the offending query/path parameter so the UI can show the error next to its filter (master plan §7.2).
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            String expected = mismatch.getRequiredType() == null ? "a valid value" : switch (mismatch.getRequiredType().getSimpleName()) {
                case "UUID" -> "a valid id";
                case "Instant", "LocalDate", "LocalDateTime" -> "a valid ISO date";
                case "int", "Integer", "long", "Long" -> "a whole number";
                default -> "a valid value";
            };
            return Problems.response(HttpStatus.BAD_REQUEST, "ESG-400", "Malformed request", req.getRequestURI(),
                    java.util.Map.of(mismatch.getName(), "must be " + expected));
        }
        if (ex instanceof MissingServletRequestParameterException missing) {
            return Problems.response(HttpStatus.BAD_REQUEST, "ESG-400", "Malformed request", req.getRequestURI(),
                    java.util.Map.of(missing.getParameterName(), "is required"));
        }
        return Problems.response(HttpStatus.BAD_REQUEST, "ESG-400", "Malformed request", req.getRequestURI(), null);
    }

    /** Errors raised by the database itself (RLS/definer permission checks, period-lock trigger, constraints). */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> handleDatabase(DataAccessException ex, HttpServletRequest req) {
        java.sql.SQLException sql = null;
        for (Throwable t = ex; t != null && sql == null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException s && s.getSQLState() != null) {
                sql = s;
            }
        }
        String state = sql == null ? "" : sql.getSQLState();
        String path = req.getRequestURI();
        return switch (state) {
            case "42501" -> Problems.response(HttpStatus.FORBIDDEN, "ESG-403", "You do not have permission to perform this action", path, null);
            case "P0L01" -> Problems.response(HttpStatus.CONFLICT, "ESG-409-LOCKED", firstLine(sql.getMessage()), path, null);
            case "P0V01" -> Problems.response(HttpStatus.CONFLICT, "ESG-409-VERSION",
                    "This record was changed by someone else. Reload and try again.", path, null);
            case "23505" -> Problems.response(HttpStatus.CONFLICT, "ESG-409-DUPLICATE", "A record with these values already exists", path, null);
            case "23503" -> Problems.response(HttpStatus.BAD_REQUEST, "ESG-400-REFERENCE", "A referenced record doesn't exist or is still in use", path, null);
            case "23514" -> Problems.response(HttpStatus.BAD_REQUEST, "ESG-400-RANGE", "A value is outside the allowed range", path, null);
            default -> handleUnexpected(ex, req);
        };
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(RuntimeException ex, HttpServletRequest req) {
        log.error("Unhandled error on {}", req.getRequestURI(), ex);
        return Problems.response(HttpStatus.INTERNAL_SERVER_ERROR, "ESG-500", "Unexpected error", req.getRequestURI(), null);
    }

    private static String firstLine(String message) {
        String text = message == null ? "" : message.replaceFirst("^ERROR: ", "");
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }
}
