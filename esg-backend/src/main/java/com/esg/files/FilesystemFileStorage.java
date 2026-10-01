package com.esg.files;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;

/** Local filesystem storage for development and tests only (no encryption at rest; not for shared environments). */
public class FilesystemFileStorage implements FileStorage {

    private final Path root;

    public FilesystemFileStorage(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    @Override
    public void put(String key, Path content, long size, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public Optional<URI> presignedDownload(String key, String fileName, String contentType, Duration ttl) {
        return Optional.empty();
    }

    @Override
    public String description() {
        return "Local filesystem (development only)";
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
