package com.esg.crosscutting;

import static org.assertj.core.api.Assertions.assertThat;

import com.esg.files.S3FileStorage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MinIOContainer;

/** S3 adapter against a real MinIO (Testcontainers). Skipped without Docker; always runs in CI. */
class S3FileStorageTest {

  @Test
  void putOpenAndPresignedDownload() throws Exception {
    Assumptions.assumeTrue(dockerAvailable(), "Docker not available: skipping MinIO test");
    try (MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2024-12-18T13-15-44Z")) {
      minio.start();
      // MinIO without a KMS rejects SSE headers, so encryption is off here (it's on by default for AWS).
      S3FileStorage storage = new S3FileStorage(minio.getS3URL(), "us-east-1", "esg-test", minio.getUserName(),
          minio.getPassword(), false);
      storage.ensureBucket();

      Path tmp = Files.createTempFile("s3", ".pdf");
      Files.writeString(tmp, "%PDF-1.4 minio");
      storage.put("t/f/v1", tmp, Files.size(tmp), "application/pdf");

      try (InputStream in = storage.open("t/f/v1")) {
        assertThat(new String(in.readAllBytes(), StandardCharsets.US_ASCII)).isEqualTo("%PDF-1.4 minio");
      }
      URI url = storage.presignedDownload("t/f/v1", "evidence.pdf", "application/pdf", Duration.ofMinutes(5)).orElseThrow();
      HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(url).build(), HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo("%PDF-1.4 minio");
      assertThat(response.headers().firstValue("Content-Disposition").orElse("")).contains("evidence.pdf");
    }
  }

  private static boolean dockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable e) {
      return false;
    }
  }
}
