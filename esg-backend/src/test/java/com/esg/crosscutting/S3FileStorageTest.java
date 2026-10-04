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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * S3 adapter against a real S3-compatible server (Testcontainers). Skipped without Docker; always runs in CI.
 * Uses Adobe S3Mock: MinIO no longer publishes images on Docker Hub, so the old pinned MinIO tag can't be pulled.
 */
class S3FileStorageTest {

  private static final String IMAGE = "adobe/s3mock:5.2.3";

  @Test
  void putOpenAndPresignedDownload() throws Exception {
    Assumptions.assumeTrue(dockerAvailable(), "Docker not available: skipping S3 test");
    try (GenericContainer<?> s3 = new GenericContainer<>(IMAGE).withExposedPorts(9090)
        // The port opens before S3Mock serves requests: wait for an HTTP answer, or the first call is reset.
        .waitingFor(Wait.forHttp("/").forPort(9090).forStatusCodeMatching(code -> code < 500).withStartupTimeout(Duration.ofSeconds(60)))) {
      s3.start();
      String endpoint = "http://" + s3.getHost() + ":" + s3.getMappedPort(9090);
      // Encryption is off here: test servers don't run a KMS (it's on by default for AWS).
      S3FileStorage storage = new S3FileStorage(endpoint, "us-east-1", "esg-test", "test-access", "test-secret", false);
      storage.ensureBucket();

      Path tmp = Files.createTempFile("s3", ".pdf");
      Files.writeString(tmp, "%PDF-1.4 s3");
      storage.put("t/f/v1", tmp, Files.size(tmp), "application/pdf");

      try (InputStream in = storage.open("t/f/v1")) {
        assertThat(new String(in.readAllBytes(), StandardCharsets.US_ASCII)).isEqualTo("%PDF-1.4 s3");
      }
      URI url = storage.presignedDownload("t/f/v1", "evidence.pdf", "application/pdf", Duration.ofMinutes(5)).orElseThrow();
      HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(url).build(), HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo("%PDF-1.4 s3");
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
