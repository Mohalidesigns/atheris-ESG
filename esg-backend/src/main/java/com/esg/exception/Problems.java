package com.esg.exception;

import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * RFC 7807 problem details (TRD §12.3, CLAUDE.md rule 7). Every error response has {@code title}, {@code status},
 * {@code detail}, {@code instance}, an {@code errorCode} (ESG-xxx) and, for validation, {@code errors}
 * ({field: message}) so the UI can show each message next to its field.
 */
public final class Problems {

    private Problems() {}

    public static ProblemDetail of(HttpStatus status, String code, String detail, String instance, Map<String, String> errors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title(status));
        if (instance != null) {
            pd.setInstance(URI.create(instance));
        }
        pd.setProperty("errorCode", code);
        if (errors != null && !errors.isEmpty()) {
            pd.setProperty("errors", errors);
        }
        return pd;
    }

    public static ResponseEntity<ProblemDetail> response(HttpStatus status, String code, String detail, String instance,
                                                         Map<String, String> errors) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(of(status, code, detail, instance, errors));
    }

    /** JSON for writers outside Spring MVC (security entry points). */
    public static String json(HttpStatus status, String code, String detail, String instance) {
        return "{\"type\":\"about:blank\",\"title\":\"" + title(status) + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + detail.replace("\"", "'") + "\",\"instance\":\"" + instance.replace("\"", "") + "\",\"errorCode\":\""
                + code + "\"}";
    }

    static String title(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Validation failed";
            case UNAUTHORIZED -> "Authentication required";
            case FORBIDDEN -> "Insufficient permissions";
            case NOT_FOUND -> "Resource not found";
            case CONFLICT -> "Conflict";
            case PAYLOAD_TOO_LARGE -> "File too large";
            case UNSUPPORTED_MEDIA_TYPE -> "Unsupported file type";
            default -> status.is5xxServerError() ? "Internal error" : status.getReasonPhrase();
        };
    }
}
