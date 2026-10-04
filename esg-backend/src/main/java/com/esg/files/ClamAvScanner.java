package com.esg.files;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ClamAV adapter (S6): streams the file to clamd with the INSTREAM command over TCP (clamd.conf {@code TCPSocket}).
 * Replies are {@code stream: OK}, {@code stream: <signature> FOUND} or an error. Any connection or protocol problem
 * is reported as unavailable, never as clean.
 */
public class ClamAvScanner implements VirusScanner {

    private static final int CHUNK = 64 * 1024;

    private final String host;
    private final int port;
    private final int timeoutMs;

    public ClamAvScanner(String host, int port, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public Result scan(Path file) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            byte[] buffer = new byte[CHUNK];
            try (InputStream in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(ByteBuffer.allocate(4).putInt(read).array());
                    out.write(buffer, 0, read);
                }
            }
            out.write(new byte[] {0, 0, 0, 0});
            out.flush();
            String reply = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).replace("\0", "").trim();
            return interpret(reply);
        } catch (IOException e) {
            return Result.unavailable("ClamAV unreachable at " + host + ":" + port + " (" + e.getMessage() + ")");
        }
    }

    static Result interpret(String reply) {
        if (reply.endsWith(": OK")) {
            return Result.clean();
        }
        if (reply.endsWith(" FOUND")) {
            String body = reply.substring(reply.indexOf(':') + 1, reply.length() - " FOUND".length()).trim();
            return Result.infected(body);
        }
        return Result.unavailable("ClamAV replied: " + (reply.length() > 200 ? reply.substring(0, 200) : reply));
    }

    @Override
    public String name() {
        return "clamav";
    }

    @Override
    public String description() {
        return "ClamAV (" + host + ":" + port + ")";
    }

    @Override
    public boolean sandbox() {
        return false;
    }
}
