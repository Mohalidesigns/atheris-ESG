package com.esg.files;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Safe Content-Disposition header values (RFC 6266 / 5987) for arbitrary user file names. */
final class ContentDisposition {
    private ContentDisposition() {}

    static String attachment(String fileName) {
        String ascii = fileName.replaceAll("[^A-Za-z0-9._ -]", "_");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
