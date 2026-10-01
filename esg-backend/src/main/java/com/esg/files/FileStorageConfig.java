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

    @Bean
    @ConditionalOnMissingBean
    VirusScanner virusScanner() {
        return new VirusScanner.NotConfigured();
    }
}
