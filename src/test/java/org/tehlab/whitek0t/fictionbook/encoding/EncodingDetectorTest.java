package org.tehlab.whitek0t.fictionbook.encoding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EncodingDetector Tests")
class EncodingDetectorTest {

    private static final byte[] BOM_UTF8 = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] BOM_UTF16LE = {(byte) 0xFF, (byte) 0xFE};
    private static final byte[] BOM_UTF16BE = {(byte) 0xFE, (byte) 0xFF};

    @TempDir
    Path tmpDir;

    private Path fileOf(String name, byte[]... parts) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        Path path = tmpDir.resolve(name);
        Files.write(path, out.toByteArray());
        return path;
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Nested
    @DisplayName("BOM detection")
    class BomTests {

        @Test
        @DisplayName("UTF-8 BOM → UTF-8")
        void utf8Bom() throws IOException {
            Path f = fileOf("u8.fb2", BOM_UTF8, ascii("<?xml version=\"1.0\"?><x/>"));
            assertThat(EncodingDetector.detect(f)).isEqualTo(StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("UTF-16LE BOM → UTF-16LE")
        void utf16leBom() throws IOException {
            Path f = fileOf("u16le.fb2", BOM_UTF16LE, ascii("<x/>"));
            assertThat(EncodingDetector.detect(f)).isEqualTo(StandardCharsets.UTF_16LE);
        }

        @Test
        @DisplayName("UTF-16BE BOM → UTF-16BE")
        void utf16beBom() throws IOException {
            Path f = fileOf("u16be.fb2", BOM_UTF16BE, ascii("<x/>"));
            assertThat(EncodingDetector.detect(f)).isEqualTo(StandardCharsets.UTF_16BE);
        }
    }

    @Nested
    @DisplayName("XML declaration")
    class XmlDeclarationTests {

        @Test
        @DisplayName("reads encoding from declaration (windows-1251)")
        void windows1251() throws IOException {
            Path f = fileOf("w.fb2",
                    ascii("<?xml version=\"1.0\" encoding=\"windows-1251\"?><x/>"));
            assertThat(EncodingDetector.detect(f)).isEqualTo(Charset.forName("windows-1251"));
        }

        @Test
        @DisplayName("reads a less-common declared charset (KOI8-R) over heuristics")
        void koi8r() throws IOException {
            Path f = fileOf("k.fb2",
                    ascii("<?xml version=\"1.0\" encoding=\"KOI8-R\"?><x/>"));
            assertThat(EncodingDetector.detect(f)).isEqualTo(Charset.forName("KOI8-R"));
        }

        @Test
        @DisplayName("accepts single-quoted encoding attribute")
        void singleQuotes() throws IOException {
            Path f = fileOf("sq.fb2", ascii("<?xml version='1.0' encoding='UTF-8'?><x/>"));
            assertThat(EncodingDetector.detect(f)).isEqualTo(StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("is case-insensitive about the declaration")
        void caseInsensitive() throws IOException {
            Path f = fileOf("ci.fb2", ascii("<?XML VERSION=\"1.0\" ENCODING=\"utf-8\"?><x/>"));
            assertThat(EncodingDetector.detect(f)).isEqualTo(StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("ignores an unknown declared charset and falls back to UTF-8")
        void unknownCharsetFallsBack() throws IOException {
            Path f = fileOf("bad.fb2",
                    ascii("<?xml version=\"1.0\" encoding=\"NOT-A-REAL-CHARSET\"?><root>ascii</root>"));
            assertThat(EncodingDetector.detect(f)).isEqualTo(StandardCharsets.UTF_8);
        }
    }

    @Nested
    @DisplayName("Fallback & priority")
    class FallbackAndPriorityTests {

        @Test
        @DisplayName("empty file → UTF-8")
        void emptyFile() throws IOException {
            Path f = fileOf("empty.fb2", new byte[0]);
            assertThat(EncodingDetector.detect(f)).isEqualTo(StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("plain ASCII without BOM or declaration → UTF-8")
        void plainAsciiFallsBack() throws IOException {
            Path f = fileOf("ascii.fb2", ascii("<root>hello world</root>"));
            assertThat(EncodingDetector.detect(f)).isEqualTo(StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("BOM wins over a conflicting XML declaration")
        void bomBeatsDeclaration() throws IOException {
            // UTF-8 BOM, но в декларации указана windows-1251 — должен победить BOM.
            Path f = fileOf("conflict.fb2", BOM_UTF8,
                    ascii("<?xml version=\"1.0\" encoding=\"windows-1251\"?><x/>"));
            assertThat(EncodingDetector.detect(f)).isEqualTo(StandardCharsets.UTF_8);
        }
    }
}
