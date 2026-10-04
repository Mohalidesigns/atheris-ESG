package com.esg.files;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** File service responses (WP-15). */
public final class FileDtos {
    private FileDtos() {}

    /** @param scanStatus pending_scan | clean | infected (quarantined) | generated (produced by the platform) */
    public record FileVersion(int version, String contentType, long sizeBytes, String sha256, String scanStatus,
                              Instant createdAt, UUID createdBy, Instant scannedAt, String scanner, String scanDetail) {}

    /** @param scanStatus the current version's scan status (S6): only clean/generated files can be downloaded */
    public record FileInfo(UUID id, String originalName, String contentType, long sizeBytes, String sha256,
                           int currentVersion, String resourceType, UUID resourceId, Instant createdAt,
                           List<FileVersion> versions, String scanStatus) {}

    public record DownloadUrl(String url, Instant expiresAt, boolean direct) {}

    public record StorageInfo(String storage, long maxBytes, String allowedExtensions, String virusScanning,
                              boolean virusScanningSandbox) {}
}
