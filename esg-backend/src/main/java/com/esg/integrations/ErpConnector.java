package com.esg.integrations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * WP-46 (D44): the adapter every named connector (SAP, Oracle, Dynamics, QuickBooks, Sage, HRIS, utilities) implements
 * when a client's request is activated (CLAUDE.md rule 9). A connector reads the client's system for a period and
 * returns rows in the import template's terms; the Data Hub import pipeline validates and saves them as drafts, exactly
 * like an uploaded file. None is implemented yet — the integration line stays open until a client asks (D44). Each
 * implementation must pass {@code ErpConnectorContract} against the vendor's sandbox before it is activated.
 */
public interface ErpConnector {

    /** The catalogue entry it implements (reference.integration_connectors.connector_key). */
    String key();

    /** True while it talks to the vendor's sandbox; the UI labels it "Sandbox". */
    boolean sandbox();

    /** Checks the tenant's connection settings (credentials, company/database) without importing anything. */
    void verify(Map<String, String> settings);

    /** Rows for the period [from, to], in the import template's columns. */
    List<Row> fetch(Map<String, String> settings, LocalDate from, LocalDate to);

    /** One import row: organisation (name or code), catalogue metric, value, optional currency, period and a note. */
    record Row(String organisation, String metric, BigDecimal value, String currency, LocalDate periodStart, LocalDate periodEnd,
               String notes) {}
}
