package org.tehlab.whitek0t.fictionbook.encoding;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Обёртка, которая читает файл в определённой кодировке
 * и отдаёт наружу байты, перекодированные в UTF-8.
 * Также обрезает BOM, если он был в исходном файле.
 */
public class EncodingAwareInputStream extends InputStream {
    /** BOM, декодированный любым из UTF-кодеков, приходит как этот символ (U+FEFF). */
    private static final char BOM_CHAR = 0xFEFF;

    private final Reader reader;
    private byte[] currentChunk = new byte[0];
    private int pos = 0;
    private boolean firstChunk = true;

    public EncodingAwareInputStream(Path file) throws IOException {
        Charset charset = EncodingDetector.detect(file);
        this.reader = Files.newBufferedReader(file, charset);
    }

    @Override
    public int read() throws IOException {
        // while, а не if: после обрезки BOM первый чанк может оказаться пустым.
        while (pos >= currentChunk.length) {
            if (!refill()) return -1;
        }
        return currentChunk[pos++] & 0xFF;
    }

    private boolean refill() throws IOException {
        char[] chars = new char[4096];
        int n = reader.read(chars);
        if (n < 0) return false;

        int start = 0;
        // Декодеры UTF-8/UTF-16LE/UTF-16BE не съедают BOM, а отдают его как U+FEFF.
        // Обрезаем его один раз — в самом начале потока.
        if (firstChunk && n > 0 && chars[0] == BOM_CHAR) {
            start = 1;
        }
        firstChunk = false;

        currentChunk = new String(chars, start, n - start).getBytes(StandardCharsets.UTF_8);
        pos = 0;
        return true;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}