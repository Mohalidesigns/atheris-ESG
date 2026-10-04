package com.esg.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field-level permission (TRD §5.1.1 field_scope, §5.3.2): the annotated response field (or record component)
 * is serialised as {@code null} unless the caller holds {@link #value()}. Applied by FieldMaskingModule.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface FieldPermission {
    /** Required permission, e.g. {@code users:read_pii}. */
    String value();
}
