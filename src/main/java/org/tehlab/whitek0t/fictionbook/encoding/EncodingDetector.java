package org.tehlab.whitek0t.fictionbook.encoding;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EncodingDetector {

    private static final Pattern XML_DECL = Pattern.compile(
            "<\\?xml[^>]+encoding\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );
    private static final int PROBE_SIZE = 8192;

    /**
     * Определяет кодировку FB2-файла.
     * Приоритет:
     * 1. BOM (Byte Order Mark)
     * 2. XML declaration {@code <?xml encoding="..."?>}
     * 3. Эвристический детектор Mozilla
     * 4. Fallback: UTF-8
     */
    public static Charset detect(Path file) throws IOException {
        byte[] probe = new byte[PROBE_SIZE];
        int read;
        try (InputStream is = new BufferedInputStream(Files.newInputStream(file))) {
            read = is.read(probe);
        }
        if (read <= 0) return StandardCharsets.UTF_8;

        // 1. BOM
        Charset bom = detectBom(probe, read);
        if (bom != null) return bom;

        // 2. XML declaration (читаем как ASCII, чтобы безопасно найти)
        String asciiProbe = new String(probe, 0, read, StandardCharsets.US_ASCII);
        Matcher m = XML_DECL.matcher(asciiProbe);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1).trim());
            } catch (Exception ignored) {
            }
        }

        // 3. Mozilla detector
        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(probe, 0, read);
        detector.dataEnd();
        String detected = detector.getDetectedCharset();
        if (detected != null) {
            try {
                return Charset.forName(detected);
            } catch (Exception ignored) {
            }
        }

        return StandardCharsets.UTF_8;
    }

    private static Charset detectBom(byte[] data, int len) {
        if (len >= 3 && data[0] == (byte) 0xEF && data[1] == (byte) 0xBB && data[2] == (byte) 0xBF)
            return StandardCharsets.UTF_8;
        if (len >= 2 && data[0] == (byte) 0xFF && data[1] == (byte) 0xFE)
            return StandardCharsets.UTF_16LE;
        if (len >= 2 && data[0] == (byte) 0xFE && data[1] == (byte) 0xFF)
            return StandardCharsets.UTF_16BE;
        return null;
    }
}