package com.esg.files;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** File service responses (WP-15). */
public final class FileDtos {
    private FileDtos() {}

    public record FileVersion(int version, String contentType, long sizeBytes, String sha256, String scanStatus,
                              Instant createdAt, UUID createdBy) {}

    public record FileInfo(UUID id, String originalName, String contentType, long sizeBytes, String sha256,
                           int currentVersion, String resourceType, UUID resourceId, Instant createdAt,
                           List<FileVersion> versions) {}

    public record DownloadUrl(String url, Instant expiresAt, boolean direct) {}

    public record StorageInfo(String storage, long maxBytes, String allowedExtensions, String virusScanning) {}
}
