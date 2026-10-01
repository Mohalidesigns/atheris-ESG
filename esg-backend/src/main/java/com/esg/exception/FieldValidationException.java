package com.esg.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * A 400 for business rules that belong to specific fields (e.g. password policy), returned in the same
 * {field: message} shape as Bean Validation errors so the UI can show them next to the right input.
 */
public class FieldValidationException extends ApiException {

    private final Map<String, String> errors;

    public FieldValidationException(Map<String, String> errors) {
        super(HttpStatus.BAD_REQUEST, "ESG-400-VALIDATION", "Validation failed");
        this.errors = Map.copyOf(errors);
    }

    public static FieldValidationException of(String field, String message) {
        return new FieldValidationException(Map.of(field, message));
    }

    public Map<String, String> getErrors() { return errors; }
}
