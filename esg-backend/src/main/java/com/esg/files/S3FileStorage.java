package com.esg.files;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * AWS S3 / MinIO storage (TRD §6.6.4): server-side encryption (SSE-AES256) and pre-signed download URLs.
 * MinIO without a KMS rejects SSE headers, so encryption is configurable (keep it on for AWS).
 */
public class S3FileStorage implements FileStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final boolean serverSideEncryption;

    public S3FileStorage(String endpoint, String region, String bucket, String accessKey, String secretKey,
                         boolean serverSideEncryption) {
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        var s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        var clientBuilder = S3Client.builder().region(Region.of(region)).credentialsProvider(credentials).serviceConfiguration(s3Config);
        var presignerBuilder = S3Presigner.builder().region(Region.of(region)).credentialsProvider(credentials).serviceConfiguration(s3Config);
        if (endpoint != null && !endpoint.isBlank()) {
            clientBuilder.endpointOverride(URI.create(endpoint));
            presignerBuilder.endpointOverride(URI.create(endpoint));
        }
        this.client = clientBuilder.build();
        this.presigner = presignerBuilder.build();
        this.bucket = bucket;
        this.serverSideEncryption = serverSideEncryption;
    }

    /** Creates the bucket if it doesn't exist (convenient for MinIO in development and CI). */
    public void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            if (e.statusCode() == 404) {
                client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } else {
                throw e;
            }
        }
    }

    @Override
    public void put(String key, Path content, long size, String contentType) {
        var request = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).contentLength(size);
        if (serverSideEncryption) {
            request.serverSideEncryption(ServerSideEncryption.AES256);
        }
        client.putObject(request.build(), RequestBody.fromFile(content));
    }

    @Override
    public InputStream open(String key) {
        return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public Optional<URI> presignedDownload(String key, String fileName, String contentType, Duration ttl) {
        var get = GetObjectRequest.builder().bucket(bucket).key(key).responseContentType(contentType)
                .responseContentDisposition(ContentDisposition.attachment(fileName)).build();
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(ttl).getObjectRequest(get).build());
        return Optional.of(URI.create(presigned.url().toString()));
    }

    @Override
    public String description() {
        return serverSideEncryption ? "S3 (server-side encryption AES-256)" : "S3-compatible storage (no server-side encryption)";
    }
}
