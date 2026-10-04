package com.esg.files;

import java.nio.file.Path;

/**
 * Virus-scan adapter for uploads (TRD §6.6.4, S6). Selected by {@code esg.files.scanner}:
 * <ul>
 *   <li>{@code clamav}: {@link ClamAvScanner}, a ClamAV daemon over TCP. Required in staging and prod.</li>
 *   <li>{@code sandbox}: {@link SandboxVirusScanner}, local/test only and labelled "Sandbox" in the UI (CLAUDE.md
 *       rule 9). It recognises only the EICAR test file, so it proves the quarantine path without claiming more.</li>
 * </ul>
 * A file is downloadable only once a scanner has called it clean; while the scanner is unreachable files stay
 * {@code pending_scan} and the rescan job ({@link VirusScanService}) retries.
 */
public interface VirusScanner {

    enum Verdict { CLEAN, INFECTED, UNAVAILABLE }

    /** @param signature the threat name when infected, or the reason when the scanner was unavailable */
    record Result(Verdict verdict, String signature) {
        public static Result clean() {
            return new Result(Verdict.CLEAN, null);
        }

        public static Result infected(String signature) {
            return new Result(Verdict.INFECTED, signature);
        }

        public static Result unavailable(String reason) {
            return new Result(Verdict.UNAVAILABLE, reason);
        }
    }

    Result scan(Path file);

    /** Short id stored with each scan, e.g. "clamav" or "sandbox-eicar". */
    String name();

    /** What the Integrations panel and storage info show. */
    String description();

    /** True for the sandbox adapter (shown as "Sandbox"). */
    boolean sandbox();
}
