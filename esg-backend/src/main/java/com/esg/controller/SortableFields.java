package com.esg.controller;

import com.esg.exception.FieldValidationException;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.Pageable;

/** Server-side lists accept ?sort=field,dir only for whitelisted fields (TRD §6.4.1); anything else is a 400. */
final class SortableFields {
    private SortableFields() {}

    static Pageable check(Pageable pageable, Set<String> allowed) {
        pageable.getSort().forEach(order -> {
            if (!allowed.contains(order.getProperty())) {
                throw FieldValidationException.of("sort", "can sort by " + String.join(", ", new TreeSet<>(allowed)));
            }
        });
        return pageable;
    }
}
