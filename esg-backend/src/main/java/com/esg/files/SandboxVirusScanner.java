package com.esg.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sandbox scanner for local development and tests (S6; labelled "Sandbox" in the UI). It detects only the industry
 * EICAR anti-virus test file, so the pending → clean / infected → quarantine path can be exercised end to end
 * without a ClamAV daemon. Every other file is reported clean, which is why the application refuses to start with
 * it in staging or prod ({@link FileStorageConfig}).
 */
public class SandboxVirusScanner implements VirusScanner {

    /** The EICAR test string (eicar.org), assembled so this source file isn't itself flagged by real scanners. */
    static final String EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$" + "EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    @Override
    public Result scan(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            // The EICAR file is the string at the start of the file, optionally followed by whitespace (≤ 128 bytes).
            String head = new String(in.readNBytes(128), StandardCharsets.ISO_8859_1);
            return head.startsWith(EICAR) ? Result.infected("Eicar-Test-Signature") : Result.clean();
        } catch (IOException e) {
            return Result.unavailable("Could not read the file: " + e.getMessage());
        }
    }

    @Override
    public String name() {
        return "sandbox-eicar";
    }

    @Override
    public String description() {
        return "Sandbox: detects the EICAR test file only (not a real virus scanner)";
    }

    @Override
    public boolean sandbox() {
        return true;
    }
}
