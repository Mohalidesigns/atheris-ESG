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
        this("ESG-400-VALIDATION", errors);
    }

    /** A field error with its own code (e.g. ESG-400-TIMEZONE), still shown next to the field. */
    public FieldValidationException(String code, Map<String, String> errors) {
        super(HttpStatus.BAD_REQUEST, code, "Validation failed");
        this.errors = Map.copyOf(errors);
    }

    public static FieldValidationException of(String field, String message) {
        return new FieldValidationException(Map.of(field, message));
    }

    public Map<String, String> getErrors() { return errors; }
}
