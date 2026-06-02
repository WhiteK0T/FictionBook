package org.tehlab.whitek0t.fictionbook.encoding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EncodingAwareInputStream Tests")
class EncodingAwareInputStreamTest {

    private static final Charset WIN1251 = Charset.forName("windows-1251");
    private static final byte[] BOM_UTF8 = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @TempDir
    Path tmpDir;

    private Path write(String name, byte[] bytes) throws IOException {
        Path path = tmpDir.resolve(name);
        Files.write(path, bytes);
        return path;
    }

    private static byte[] readAll(Path file) throws IOException {
        try (EncodingAwareInputStream in = new EncodingAwareInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                // read() обязан возвращать беззнаковый байт 0..255, а не -1 раньше времени
                assertThat(b).isBetween(0, 255);
                out.write(b);
            }
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("transcodes a windows-1251 file to UTF-8 bytes")
    void transcodesWindows1251ToUtf8() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"windows-1251\"?><a>Привет, мир!</a>";
        Path file = write("cp1251.fb2", xml.getBytes(WIN1251));

        byte[] out = readAll(file);

        // Вывод — UTF-8: исходная кириллица должна корректно восстановиться.
        assertThat(out).isEqualTo(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(new String(out, StandardCharsets.UTF_8)).isEqualTo(xml);
    }

    @Test
    @DisplayName("passes UTF-8 (no BOM) content through unchanged")
    void passesThroughUtf8Unchanged() throws IOException {
        byte[] content = "<a>plain ascii content</a>".getBytes(StandardCharsets.UTF_8);
        Path file = write("utf8.fb2", content);

        assertThat(readAll(file)).isEqualTo(content);
    }

    @Test
    @DisplayName("round-trips UTF-8 Cyrillic content (no BOM) unchanged")
    void passesThroughUtf8Cyrillic() throws IOException {
        byte[] content = "<a>Привет</a>".getBytes(StandardCharsets.UTF_8);
        Path file = write("utf8cyr.fb2", content);

        assertThat(readAll(file)).isEqualTo(content);
    }

    @Test
    @DisplayName("returns -1 at end of stream")
    void returnsMinusOneAtEof() throws IOException {
        Path file = write("eof.fb2", "<a/>".getBytes(StandardCharsets.UTF_8));

        try (EncodingAwareInputStream in = new EncodingAwareInputStream(file)) {
            in.readAllBytes();
            assertThat(in.read()).isEqualTo(-1);
            assertThat(in.read()).isEqualTo(-1); // повторный вызов после EOF тоже -1
        }
    }

    @Test
    @DisplayName("strips the UTF-8 BOM from the decoded output")
    void stripsUtf8Bom() throws IOException {
        byte[] body = "<a>x</a>".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[BOM_UTF8.length + body.length];
        System.arraycopy(BOM_UTF8, 0, withBom, 0, BOM_UTF8.length);
        System.arraycopy(body, 0, withBom, BOM_UTF8.length, body.length);
        Path file = write("bom.fb2", withBom);

        byte[] out = readAll(file);

        // Контракт класса: BOM обрезается, наружу идёт только содержимое.
        assertThat(out).isEqualTo(body);
    }
}
