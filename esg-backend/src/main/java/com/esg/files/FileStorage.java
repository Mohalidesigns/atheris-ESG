package com.esg.files;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Where file bytes live (TRD §6.6.4). Adapters: {@link FilesystemFileStorage} (local/test) and {@link S3FileStorage}
 * (AWS S3 or MinIO, server-side encryption). Selected by {@code esg.files.storage}. Keys are opaque; versions are
 * never overwritten.
 */
public interface FileStorage {

    void put(String key, Path content, long size, String contentType) throws IOException;

    InputStream open(String key) throws IOException;

    /** A time-limited direct download URL, if the backend supports one (S3). Empty means "stream through the API". */
    Optional<URI> presignedDownload(String key, String fileName, String contentType, Duration ttl);

    /** Short label for the UI/status, e.g. "S3 (SSE-AES256)" or "Local filesystem (development)". */
    String description();
}
