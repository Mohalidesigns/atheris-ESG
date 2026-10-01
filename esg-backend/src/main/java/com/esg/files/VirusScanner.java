package com.esg.files;

import java.nio.file.Path;

/**
 * Virus-scan hook for uploads (TRD §6.6.4). The default {@link NotConfigured} scanner reports
 * {@code NOT_SCANNED} honestly; a ClamAV adapter arrives with WP-52. Infected files are rejected before storage.
 */
public interface VirusScanner {

    enum Result { CLEAN, INFECTED, NOT_SCANNED }

    Result scan(Path file);

    /** No scanner configured: nothing is claimed about the file. */
    final class NotConfigured implements VirusScanner {
        @Override
        public Result scan(Path file) {
            return Result.NOT_SCANNED;
        }
    }
}
