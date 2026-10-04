package com.esg.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** S6: the ClamAV adapter speaks clamd's INSTREAM protocol, and anything unexpected is "unavailable", never clean. */
class ClamAvScannerTest {

  /** A one-shot fake clamd: reads the INSTREAM command and chunks, then answers {@code reply}. */
  private static int fakeClamd(String reply, AtomicReference<byte[]> received) throws Exception {
    ServerSocket server = new ServerSocket(0);
    Thread t = new Thread(() -> {
      try (server; Socket s = server.accept()) {
        DataInputStream in = new DataInputStream(s.getInputStream());
        byte[] command = new byte["zINSTREAM\0".length()];
        in.readFully(command);
        assertThat(new String(command, StandardCharsets.US_ASCII)).isEqualTo("zINSTREAM\0");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int len;
        while ((len = in.readInt()) > 0) {
          byte[] chunk = new byte[len];
          in.readFully(chunk);
          body.write(chunk);
        }
        received.set(body.toByteArray());
        s.getOutputStream().write((reply + "\0").getBytes(StandardCharsets.US_ASCII));
      } catch (Exception ignored) {
        // the assertion in the test reports the problem
      }
    });
    t.setDaemon(true);
    t.start();
    return server.getLocalPort();
  }

  private static Path file(String content) throws Exception {
    Path p = Files.createTempFile("clam", ".txt");
    Files.writeString(p, content);
    return p;
  }

  @Test
  void cleanAndInfectedRepliesAndTheStreamedBytes() throws Exception {
    AtomicReference<byte[]> received = new AtomicReference<>();
    int port = fakeClamd("stream: OK", received);
    assertThat(new ClamAvScanner("localhost", port, 5000).scan(file("hello")).verdict()).isEqualTo(VirusScanner.Verdict.CLEAN);
    assertThat(new String(received.get(), StandardCharsets.US_ASCII)).isEqualTo("hello");

    port = fakeClamd("stream: Win.Test.EICAR_HDB-1 FOUND", received);
    VirusScanner.Result infected = new ClamAvScanner("localhost", port, 5000).scan(file("x"));
    assertThat(infected.verdict()).isEqualTo(VirusScanner.Verdict.INFECTED);
    assertThat(infected.signature()).isEqualTo("Win.Test.EICAR_HDB-1");
  }

  @Test
  void errorsAndUnreachableDaemonAreUnavailableNotClean() throws Exception {
    AtomicReference<byte[]> received = new AtomicReference<>();
    int port = fakeClamd("INSTREAM size limit exceeded. ERROR", received);
    assertThat(new ClamAvScanner("localhost", port, 5000).scan(file("x")).verdict()).isEqualTo(VirusScanner.Verdict.UNAVAILABLE);

    int closed;
    try (ServerSocket s = new ServerSocket(0)) {
      closed = s.getLocalPort();
    }
    VirusScanner.Result down = new ClamAvScanner("localhost", closed, 1000).scan(file("x"));
    assertThat(down.verdict()).isEqualTo(VirusScanner.Verdict.UNAVAILABLE);
    assertThat(down.signature()).contains("unreachable");
  }

  @Test
  void sandboxDetectsOnlyTheEicarTestFile() throws Exception {
    SandboxVirusScanner sandbox = new SandboxVirusScanner();
    assertThat(sandbox.scan(file(SandboxVirusScanner.EICAR + "\n")).verdict()).isEqualTo(VirusScanner.Verdict.INFECTED);
    assertThat(sandbox.scan(file("quarterly diesel log")).verdict()).isEqualTo(VirusScanner.Verdict.CLEAN);
    assertThat(sandbox.sandbox()).isTrue();
    assertThat(sandbox.description()).startsWith("Sandbox");
  }
}
