package org.tehlab.whitek0t.fictionbook.api;

import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Формат файла FictionBook.
 */
public enum FictionBookFormat {

    /**
     * FB2 — FictionBook 2.x (один XML-файл)
     */
    FB2(".fb2"),

    /**
     * FB3 — FictionBook 3.0 (ZIP-контейнер)
     */
    FB3(".fb3");

    private final String extension;

    FictionBookFormat(String extension) {
        this.extension = extension;
    }

    /**
     * Возвращает расширение файла, ассоциированное с форматом.
     *
     * @return расширение файла для данного формата (с точкой), например {@code ".fb2"}
     */
    public String getExtension() {
        return extension;
    }

    /**
     * Определяет формат файла по расширению.
     *
     * @param path путь к файлу
     * @return формат файла
     * @throws FictionBookException если расширение не распознано
     */
    public static FictionBookFormat fromPath(Path path) throws FictionBookException {
        String fileName = path.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".fb2")) return FB2;
        if (fileName.endsWith(".fb3")) return FB3;

        throw new FictionBookException("Unknown file extension: " + fileName);
    }

    /**
     * Автоматически определяет формат файла по magic bytes и расширению.
     * <p>
     * Приоритет:
     * 1. Magic bytes (ZIP для FB3, XML для FB2)
     * 2. Расширение файла (fallback)
     *
     * @param file путь к файлу
     * @return определённый формат
     * @throws FictionBookException если формат не может быть определён
     */
    public static FictionBookFormat detect(Path file) throws FictionBookException {
        if (!Files.exists(file)) {
            throw new FictionBookException("File does not exist: " + file);
        }

        if (!Files.isRegularFile(file)) {
            throw new FictionBookException("Not a regular file: " + file);
        }

        try {
            // Читаем первые 4 байта для определения magic bytes
            byte[] magic = new byte[4];
            try (InputStream is = Files.newInputStream(file)) {
                int read = is.read(magic);
                if (read < 4) {
                    // Файл слишком маленький, определяем только по расширению
                    return fromPath(file);
                }
            }

            // ZIP signature: PK\x03\x04
            if (magic[0] == 0x50 && magic[1] == 0x4B && magic[2] == 0x03 && magic[3] == 0x04) {
                return FB3;
            }

            // XML обычно начинается с <?xml или <FictionBook
            // Проверяем первые байты как ASCII
            String start = new String(magic, StandardCharsets.US_ASCII).trim();
            if (start.startsWith("<?xml") || start.startsWith("<FictionBook") || start.startsWith("<")) {
                return FB2;
            }

            // Fallback: определяем по расширению
            return fromPath(file);

        } catch (IOException e) {
            throw new FictionBookException("Failed to detect file format: " + file);
        }
    }
}