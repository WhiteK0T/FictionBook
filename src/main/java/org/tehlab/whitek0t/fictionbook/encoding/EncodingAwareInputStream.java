package org.tehlab.whitek0t.fictionbook.encoding;

import java.io.ByteArrayOutputStream;
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
    private final Reader reader;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private byte[] currentChunk = new byte[0];
    private int pos = 0;

    public EncodingAwareInputStream(Path file) throws IOException {
        Charset charset = EncodingDetector.detect(file);
        this.reader = Files.newBufferedReader(file, charset);
    }

    @Override
    public int read() throws IOException {
        if (pos >= currentChunk.length) {
            if (!refill()) return -1;
        }
        return currentChunk[pos++] & 0xFF;
    }

    private boolean refill() throws IOException {
        char[] chars = new char[4096];
        int n = reader.read(chars);
        if (n < 0) return false;
        currentChunk = new String(chars, 0, n).getBytes(StandardCharsets.UTF_8);
        pos = 0;
        return true;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}