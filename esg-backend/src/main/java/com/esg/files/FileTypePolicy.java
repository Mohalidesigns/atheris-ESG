package com.esg.files;

import com.esg.exception.ApiException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Allowed upload types (TRD §6.6.4: PDF, Excel, CSV, images, Word). The type comes from the file extension and is
 * confirmed against the file's leading bytes; the browser's declared content type is not trusted.
 */
public final class FileTypePolicy {

    private FileTypePolicy() {}

    private record Type(String contentType, byte[][] signatures, boolean text) {}

    private static final byte[] PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] ZIP = {'P', 'K', 3, 4};
    private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    private static final Map<String, Type> TYPES = Map.ofEntries(
            Map.entry("pdf", new Type("application/pdf", new byte[][] {PDF}, false)),
            Map.entry("png", new Type("image/png", new byte[][] {PNG}, false)),
            Map.entry("jpg", new Type("image/jpeg", new byte[][] {JPG}, false)),
            Map.entry("jpeg", new Type("image/jpeg", new byte[][] {JPG}, false)),
            Map.entry("xlsx", new Type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[][] {ZIP}, false)),
            Map.entry("docx", new Type("application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[][] {ZIP}, false)),
            Map.entry("xls", new Type("application/vnd.ms-excel", new byte[][] {OLE}, false)),
            Map.entry("doc", new Type("application/msword", new byte[][] {OLE}, false)),
            Map.entry("csv", new Type("text/csv", new byte[0][], true)),
            Map.entry("txt", new Type("text/plain", new byte[0][], true)));

    public static final String ALLOWED_EXTENSIONS = String.join(", ", TYPES.keySet().stream().sorted().toList());

    /** Returns the canonical content type, or throws 415 if the name or the bytes don't match an allowed type. */
    public static String check(String fileName, byte[] head) {
        String name = fileName == null ? "" : fileName;
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        Type type = TYPES.get(ext);
        if (type == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "ESG-415", "Allowed file types: " + ALLOWED_EXTENSIONS);
        }
        boolean ok = type.text()
                ? !containsNul(head)
                : Arrays.stream(type.signatures()).anyMatch(sig -> startsWith(head, sig));
        if (!ok) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "ESG-415",
                    "The file's content doesn't match its ." + ext + " extension");
        }
        return type.contentType();
    }

    private static boolean startsWith(byte[] head, byte[] sig) {
        if (head.length < sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if (head[i] != sig[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsNul(byte[] head) {
        for (byte b : head) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }
}
