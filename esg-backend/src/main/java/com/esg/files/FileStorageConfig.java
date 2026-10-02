package com.esg.files;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chooses the storage adapter: {@code esg.files.storage=s3} (default) or {@code filesystem} (local/test). */
@Configuration
public class FileStorageConfig {

    @Bean
    FileStorage fileStorage(@Value("${esg.files.storage}") String storage,
                            @Value("${esg.files.local-dir}") String localDir,
                            @Value("${esg.files.s3.endpoint:}") String endpoint,
                            @Value("${esg.files.s3.region}") String region,
                            @Value("${esg.files.s3.bucket}") String bucket,
                            @Value("${esg.files.s3.access-key:}") String accessKey,
                            @Value("${esg.files.s3.secret-key:}") String secretKey,
                            @Value("${esg.files.s3.server-side-encryption}") boolean sse,
                            @Value("${esg.files.s3.create-bucket:false}") boolean createBucket) throws IOException {
        if ("filesystem".equals(storage)) {
            return new FilesystemFileStorage(Path.of(localDir));
        }
        if (!"s3".equals(storage)) {
            throw new IllegalStateException("esg.files.storage must be 's3' or 'filesystem'");
        }
        S3FileStorage s3 = new S3FileStorage(endpoint, region, bucket, accessKey, secretKey, sse);
        if (createBucket) {
            s3.ensureBucket();
        }
        return s3;
    }

    /** {@code esg.files.scanner}: clamav (required in staging/prod) or sandbox (local/test only; S6). */
    @Bean
    @ConditionalOnMissingBean
    VirusScanner virusScanner(@Value("${esg.files.scanner}") String scanner,
                              @Value("${esg.files.clamav.host:localhost}") String host,
                              @Value("${esg.files.clamav.port:3310}") int port,
                              @Value("${esg.files.clamav.timeout-ms:30000}") int timeoutMs,
                              org.springframework.core.env.Environment env) {
        return switch (scanner) {
            case "clamav" -> new ClamAvScanner(host, port, timeoutMs);
            case "sandbox" -> {
                if (java.util.Arrays.stream(env.getActiveProfiles()).anyMatch(p -> p.equals("prod") || p.equals("staging"))) {
                    throw new IllegalStateException("esg.files.scanner=sandbox is for local/test only; use clamav");
                }
                yield new SandboxVirusScanner();
            }
            default -> throw new IllegalStateException("esg.files.scanner must be 'clamav' or 'sandbox'");
        };
    }
}
