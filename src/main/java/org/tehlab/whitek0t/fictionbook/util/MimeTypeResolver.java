package org.tehlab.whitek0t.fictionbook.util;

import java.util.Map;

/**
 * Преобразует MIME-типы в расширения файлов.
 */
public final class MimeTypeResolver {

    private static final Map<String, String> MIME_TO_EXT = Map.ofEntries(
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/jpg", ".jpg"),
            Map.entry("image/png", ".png"),
            Map.entry("image/gif", ".gif"),
            Map.entry("image/webp", ".webp"),
            Map.entry("image/svg+xml", ".svg"),
            Map.entry("image/bmp", ".bmp"),
            Map.entry("image/tiff", ".tiff"),
            Map.entry("image/x-icon", ".ico"),
            Map.entry("application/octet-stream", ".bin")
    );

    private MimeTypeResolver() {
    }

    /**
     * Возвращает расширение файла (с точкой) для данного MIME-типа.
     * Если тип неизвестен — возвращает ".bin".
     */
    public static String toExtension(String mimeType) {
        if (mimeType == null) return ".bin";
        return MIME_TO_EXT.getOrDefault(mimeType.toLowerCase().trim(), ".bin");
    }

    /**
     * Возвращает расширение без точки.
     */
    public static String toExtensionWithoutDot(String mimeType) {
        String ext = toExtension(mimeType);
        return ext.startsWith(".") ? ext.substring(1) : ext;
    }
}
