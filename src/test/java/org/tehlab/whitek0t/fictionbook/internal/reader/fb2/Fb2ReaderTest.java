package org.tehlab.whitek0t.fictionbook.internal.reader.fb2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.description.Author;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.exception.InvalidFormatException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Комплексные тесты для {@link Fb2Reader}.
 *
 * <p>Покрывает:</p>
 * <ul>
 *   <li>Чтение description (Jackson)</li>
 *   <li>Чтение body с секциями (StAX)</li>
 *   <li>Бинарные ресурсы (eager base64 decode)</li>
 *   <li>Автоопределение кодировки (UTF-8, windows-1251, BOM)</li>
 *   <li>Обработка ошибок и прощающий режим</li>
 * </ul>
 */
@DisplayName("Fb2Reader Tests")
class Fb2ReaderTest {

    private Fb2Reader reader;

    @BeforeEach
    void setUp() {
        reader = new Fb2Reader();
    }

    // ========================================================================
    // БАЗОВЫЕ ТЕСТЫ
    // ========================================================================

    @Nested
    @DisplayName("Basic Reading")
    class BasicReadingTests {

        @Test
        @DisplayName("should read minimal valid FB2")
        void shouldReadMinimalValidFb2() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info>
                                <genre>fiction</genre>
                                <book-title>Test Book</book-title>
                                <lang>ru</lang>
                            </title-info>
                            <document-info>
                                <id>test-id</id>
                                <version>1.0</version>
                            </document-info>
                        </description>
                        <body>
                            <section>
                                <p>Hello, world!</p>
                            </section>
                        </body>
                    </FictionBook>
                    """;

            FictionBookDto book = readFromString(xml);

            assertThat(book).isNotNull();
            assertThat(book.description()).isNotNull();
            assertThat(book.description().titleInfo().bookTitle()).isEqualTo("Test Book");
            assertThat(book.bodies()).hasSize(1);
            assertThat(book.bodies().get(0).sections()).hasSize(1);
        }

        @Test
        @DisplayName("should read book with multiple bodies (main + notes)")
        void shouldReadBookWithMultipleBodies() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info>
                                <book-title>Book with Notes</book-title>
                                <lang>ru</lang>
                            </title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body>
                            <section>
                                <p>Main text with <a l:href="#n1">note</a></p>
                            </section>
                        </body>
                        <body name="notes">
                            <section id="n1">
                                <p>Note content</p>
                            </section>
                        </body>
                    </FictionBook>
                    """;

            FictionBookDto book = readFromString(xml);

            assertThat(book.bodies()).hasSize(2);
            assertThat(book.bodies().get(0).name()).isNull();
            assertThat(book.bodies().get(1).name()).isEqualTo("notes");
        }
    }

    // ========================================================================
    // DESCRIPTION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Description Parsing")
    class DescriptionTests {

        @Test
        @DisplayName("should parse authors correctly")
        void shouldParseAuthors() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info>
                                <author>
                                    <first-name>Лев</first-name>
                                    <middle-name>Николаевич</middle-name>
                                    <last-name>Толстой</last-name>
                                </author>
                                <author>
                                    <first-name>Фёдор</first-name>
                                    <last-name>Достоевский</last-name>
                                </author>
                                <book-title>Test</book-title>
                                <lang>ru</lang>
                            </title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body><section><p>Text</p></section></body>
                    </FictionBook>
                    """;

            FictionBookDto book = readFromString(xml);
            var authors = book.description().titleInfo().authors();

            assertThat(authors).hasSize(2);

            Author tolstoy = authors.get(0);
            assertThat(tolstoy.firstName()).isEqualTo("Лев");
            assertThat(tolstoy.middleName()).isEqualTo("Николаевич");
            assertThat(tolstoy.lastName()).isEqualTo("Толстой");
            assertThat(tolstoy.getFullName()).isEqualTo("Лев Николаевич Толстой");

            Author dostoevsky = authors.get(1);
            assertThat(dostoevsky.firstName()).isEqualTo("Фёдор");
            assertThat(dostoevsky.middleName()).isNull();
            assertThat(dostoevsky.lastName()).isEqualTo("Достоевский");
        }

        @Test
        @DisplayName("should parse multiple genres")
        void shouldParseMultipleGenres() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info>
                                <genre>sf_history</genre>
                                <genre>sf_fantasy</genre>
                                <genre>prose_classic</genre>
                                <book-title>Test</book-title>
                                <lang>ru</lang>
                            </title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body><section><p>Text</p></section></body>
                    </FictionBook>
                    """;

            FictionBookDto book = readFromString(xml);
            var genres = book.description().titleInfo().genres();

            assertThat(genres).containsExactly("sf_history", "sf_fantasy", "prose_classic");
        }

        @Test
        @DisplayName("should parse sequence")
        void shouldParseSequence() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info>
                                <book-title>Test</book-title>
                                <lang>ru</lang>
                                <sequence name="Великое Кольцо" number="3"/>
                            </title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body><section><p>Text</p></section></body>
                    </FictionBook>
                    """;

            FictionBookDto book = readFromString(xml);
            var sequence = book.description().titleInfo().sequence();

            assertThat(sequence).isNotNull();
            assertThat(sequence.name()).isEqualTo("Великое Кольцо");
            assertThat(sequence.number()).isEqualTo(3);
        }

        @Test
        @DisplayName("should parse coverpage image references")
        void shouldParseCoverpage() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" 
                                 xmlns:l="http://www.w3.org/1999/xlink">
                        <description>
                            <title-info>
                                <book-title>Test</book-title>
                                <lang>ru</lang>
                                <coverpage>
                                    <image l:href="#cover1"/>
                                    <image l:href="#cover2"/>
                                </coverpage>
                            </title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body><section><p>Text</p></section></body>
                        <binary id="cover1" content-type="image/jpeg">AAAA</binary>
                        <binary id="cover2" content-type="image/png">BBBB</binary>
                    </FictionBook>
                    """;

            FictionBookDto book = readFromString(xml);
            var coverIds = book.description().titleInfo().coverImageIds();

            assertThat(coverIds).containsExactly("cover1", "cover2");
        }

        @Test
        @DisplayName("should parse document info")
        void shouldParseDocumentInfo() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info>
                                <book-title>Test</book-title>
                                <lang>ru</lang>
                            </title-info>
                            <document-info>
                                <author>
                                    <first-name>OCR</first-name>
                                    <last-name>Author</last-name>
                                </author>
                                <program-used>Fiction Book Designer</program-used>
                                <date>15.01.2024</date>
                                <id>unique-id-12345</id>
                                <version>2.5</version>
                            </document-info>
                        </description>
                        <body><section><p>Text</p></section></body>
                    </FictionBook>
                    """;

            FictionBookDto book = readFromString(xml);
            var docInfo = book.description().documentInfo();

            assertThat(docInfo).isNotNull();
            assertThat(docInfo.id()).isEqualTo("unique-id-12345");
            assertThat(docInfo.version()).isEqualTo("2.5");
            assertThat(docInfo.programUsed()).isEqualTo("Fiction Book Designer");
            assertThat(docInfo.date()).isEqualTo("15.01.2024");
            assertThat(docInfo.authors()).hasSize(1);
            assertThat(docInfo.authors().get(0).getFullName()).isEqualTo("OCR Author");
        }

        @Test
        @DisplayName("should parse publish info")
        void shouldParsePublishInfo() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info>
                                <book-title>Test</book-title>
                                <lang>ru</lang>
                            </title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                            <publish-info>
                                <book-name>Полное издание</book-name>
                                <publisher>АСТ</publisher>
                                <city>Москва</city>
                                <year>2024</year>
                                <isbn>978-5-17-123456-7</isbn>
                            </publish-info>
                        </description>
                        <body><section><p>Text</p></section></body>
                    </FictionBook>
                    """;

            FictionBookDto book = readFromString(xml);
            var pubInfo = book.description().publishInfo();

            assertThat(pubInfo).isNotNull();
            assertThat(pubInfo.bookName()).isEqualTo("Полное издание");
            assertThat(pubInfo.publisher()).isEqualTo("АСТ");
            assertThat(pubInfo.city()).isEqualTo("Москва");
            assertThat(pubInfo.year()).isEqualTo("2024");
            assertThat(pubInfo.isbn()).isEqualTo("978-5-17-123456-7");
        }
    }

    // ========================================================================
    // BODY TESTS
    // ========================================================================

    @Nested
    @DisplayName("Body Parsing")
    class BodyTests {

        @Test
        @DisplayName("should parse nested sections")
        void shouldParseNestedSections() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info><book-title>Test</book-title><lang>ru</lang></title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body>
                            <section id="part1">
                                <title><p>Часть 1</p></title>
                                <section id="ch1">
                                    <title><p>Глава 1</p></title>
                                    <p>Текст главы 1</p>
                                </section>
                                <section id="ch2">
                                    <title><p>Глава 2</p></title>
                                    <p>Текст главы 2</p>
                                </section>
                            </section>
                        </body>
                    </FictionBook>
                    """;

            FictionBookDto book = readFromString(xml);
            Section part1 = book.bodies().get(0).sections().get(0);

            assertThat(part1.id()).isEqualTo("part1");
            assertThat(part1.subSections()).hasSize(2);
            assertThat(part1.subSections().get(0).id()).isEqualTo("ch1");
            assertThat(part1.subSections().get(1).id()).isEqualTo("ch2");
        }

        @Test
        @DisplayName("should parse formatted text in paragraphs")
        void shouldParseFormattedText() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"
                                 xmlns:l="http://www.w3.org/1999/xlink">
                        <description>
                            <title-info><book-title>Test</book-title><lang>ru</lang></title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body>
                            <section>
                                <p>Обычный <strong>жирный</strong> и <emphasis>курсив</emphasis></p>
                                <p>Ссылка <a l:href="#note1" type="note">[1]</a></p>
                            </section>
                        </body>
                    </FictionBook>
                    """;

            FictionBookDto book = readFromString(xml);
            Section section = book.bodies().get(0).sections().get(0);

            assertThat(section.content()).hasSize(2);
            assertThat(section.content().get(0)).isInstanceOf(Paragraph.class);
            assertThat(section.content().get(1)).isInstanceOf(Paragraph.class);

            Paragraph p1 = (Paragraph) section.content().get(0);
            assertThat(p1.elements()).hasSizeGreaterThanOrEqualTo(3);
        }
    }

    // ========================================================================
    // BINARY RESOURCE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Binary Resource Parsing")
    class BinaryResourceTests {

        @Test
        @DisplayName("should decode base64 binary resources")
        void shouldDecodeBase64BinaryResources() throws Exception {
            byte[] originalData = "fake image data for testing".getBytes(StandardCharsets.UTF_8);
            String base64Data = Base64.getEncoder().encodeToString(originalData);

            String xml = String.format("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info><book-title>Test</book-title><lang>ru</lang></title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body><section><p>Text</p></section></body>
                        <binary id="img1" content-type="image/jpeg">%s</binary>
                    </FictionBook>
                    """, base64Data);

            FictionBookDto book = readFromString(xml);

            assertThat(book.resources()).containsKey("img1");
            Resource resource = book.resources().get("img1");
            assertThat(resource.id()).isEqualTo("img1");
            assertThat(resource.contentType()).isEqualTo("image/jpeg");

            try (InputStream is = resource.dataProvider().getInputStream()) {
                byte[] decoded = is.readAllBytes();
                assertThat(decoded).isEqualTo(originalData);
            }
        }

        @Test
        @DisplayName("should handle multiple binary resources")
        void shouldHandleMultipleBinaryResources() throws Exception {
            String base64_1 = Base64.getEncoder().encodeToString("data1".getBytes());
            String base64_2 = Base64.getEncoder().encodeToString("data2".getBytes());
            String base64_3 = Base64.getEncoder().encodeToString("data3".getBytes());

            String xml = String.format("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info><book-title>Test</book-title><lang>ru</lang></title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body><section><p>Text</p></section></body>
                        <binary id="img1" content-type="image/jpeg">%s</binary>
                        <binary id="img2" content-type="image/png">%s</binary>
                        <binary id="img3" content-type="image/gif">%s</binary>
                    </FictionBook>
                    """, base64_1, base64_2, base64_3);

            FictionBookDto book = readFromString(xml);

            assertThat(book.resources()).hasSize(3);
            assertThat(book.resources()).containsKeys("img1", "img2", "img3");
            assertThat(book.resources().get("img1").contentType()).isEqualTo("image/jpeg");
            assertThat(book.resources().get("img2").contentType()).isEqualTo("image/png");
            assertThat(book.resources().get("img3").contentType()).isEqualTo("image/gif");
        }

        @Test
        @DisplayName("should handle binary without content-type")
        void shouldHandleBinaryWithoutContentType() throws Exception {
            String base64 = Base64.getEncoder().encodeToString("data".getBytes());

            String xml = String.format("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info><book-title>Test</book-title><lang>ru</lang></title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body><section><p>Text</p></section></body>
                        <binary id="img1">%s</binary>
                    </FictionBook>
                    """, base64);

            FictionBookDto book = readFromString(xml);

            Resource resource = book.resources().get("img1");
            assertThat(resource).isNotNull();
            // Fallback content-type
            assertThat(resource.contentType()).isEqualTo("application/octet-stream");
        }

        @Test
        @DisplayName("should skip binary without id")
        void shouldSkipBinaryWithoutId() throws Exception {
            String base64 = Base64.getEncoder().encodeToString("data".getBytes());

            String xml = String.format("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info><book-title>Test</book-title><lang>ru</lang></title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body><section><p>Text</p></section></body>
                        <binary content-type="image/jpeg">%s</binary>
                    </FictionBook>
                    """, base64);

            FictionBookDto book = readFromString(xml);

            // Бинарник без id должен быть пропущен
            assertThat(book.resources()).isEmpty();
        }

        @Test
        @DisplayName("should handle large binary resource efficiently")
        void shouldHandleLargeBinaryResource() throws Exception {
            // 1 MB данных
            byte[] largeData = new byte[1024 * 1024];
            for (int i = 0; i < largeData.length; i++) {
                largeData[i] = (byte) (i % 256);
            }
            String base64 = Base64.getEncoder().encodeToString(largeData);

            String xml = String.format("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info><book-title>Test</book-title><lang>ru</lang></title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body><section><p>Text</p></section></body>
                        <binary id="large" content-type="image/jpeg">%s</binary>
                    </FictionBook>
                    """, base64);

            FictionBookDto book = readFromString(xml);
            Resource resource = book.resources().get("large");

            try (InputStream is = resource.dataProvider().getInputStream()) {
                byte[] decoded = is.readAllBytes();
                assertThat(decoded).isEqualTo(largeData);
            }
        }
    }

    // ========================================================================
    // ENCODING TESTS
    // ========================================================================

    @Nested
    @DisplayName("Encoding Detection")
    class EncodingTests {

        @Test
        @DisplayName("should read UTF-8 encoded file")
        void shouldReadUtf8EncodedFile(@TempDir Path tempDir) throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info>
                                <book-title>Книга на русском</book-title>
                                <lang>ru</lang>
                            </title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body><section><p>Привет, мир!</p></section></body>
                    </FictionBook>
                    """;

            Path file = tempDir.resolve("utf8.fb2");
            Files.writeString(file, xml, StandardCharsets.UTF_8);

            FictionBookDto book = reader.read(file);

            assertThat(book.description().titleInfo().bookTitle()).isEqualTo("Книга на русском");
        }

        @Test
        @DisplayName("should read windows-1251 encoded file")
        void shouldReadWindows1251EncodedFile(@TempDir Path tempDir) throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="windows-1251"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info>
                                <book-title>Книга в cp1251</book-title>
                                <lang>ru</lang>
                            </title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body><section><p>Привет из cp1251!</p></section></body>
                    </FictionBook>
                    """;

            Path file = tempDir.resolve("cp1251.fb2");
            Files.writeString(file, xml, Charset.forName("windows-1251"));

            FictionBookDto book = reader.read(file);

            assertThat(book.description().titleInfo().bookTitle()).isEqualTo("Книга в cp1251");
        }

        @Test
        @DisplayName("should read file with UTF-8 BOM")
        void shouldReadFileWithUtf8Bom(@TempDir Path tempDir) throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info>
                                <book-title>Книга с BOM</book-title>
                                <lang>ru</lang>
                            </title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body><section><p>Текст</p></section></body>
                    </FictionBook>
                    """;

            Path file = tempDir.resolve("bom.fb2");
            byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] content = xml.getBytes(StandardCharsets.UTF_8);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(bom);
            baos.write(content);
            Files.write(file, baos.toByteArray());

            FictionBookDto book = reader.read(file);

            assertThat(book.description().titleInfo().bookTitle()).isEqualTo("Книга с BOM");
        }
    }

    // ========================================================================
    // ERROR HANDLING TESTS
    // ========================================================================

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should throw when description is missing")
        void shouldThrowWhenDescriptionIsMissing() {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <body><section><p>Text</p></section></body>
                    </FictionBook>
                    """;

            assertThatThrownBy(() -> readFromString(xml))
                    .isInstanceOf(InvalidFormatException.class)
                    .hasMessageContaining("description");
        }

        @Test
        @DisplayName("should throw when body is missing")
        void shouldThrowWhenBodyIsMissing() {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info><book-title>Test</book-title><lang>ru</lang></title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                    </FictionBook>
                    """;

            assertThatThrownBy(() -> readFromString(xml))
                    .isInstanceOf(InvalidFormatException.class)
                    .hasMessageContaining("body");
        }

        @Test
        @DisplayName("should throw on malformed XML")
        void shouldThrowOnMalformedXml() {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info>
                                <book-title>Unclosed tag
                            </title-info>
                        </description>
                    </FictionBook>
                    """;

            assertThatThrownBy(() -> readFromString(xml))
                    .isInstanceOf(FictionBookException.class);
        }

        @Test
        @DisplayName("should throw on duplicate description")
        void shouldThrowOnDuplicateDescription() {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info><book-title>First</book-title><lang>ru</lang></title-info>
                            <document-info><id>id1</id><version>1.0</version></document-info>
                        </description>
                        <description>
                            <title-info><book-title>Second</book-title><lang>ru</lang></title-info>
                            <document-info><id>id2</id><version>1.0</version></document-info>
                        </description>
                        <body><section><p>Text</p></section></body>
                    </FictionBook>
                    """;

            assertThatThrownBy(() -> readFromString(xml))
                    .isInstanceOf(InvalidFormatException.class)
                    .hasMessageContaining("description");
        }

        @Test
        @DisplayName("should throw when file does not exist")
        void shouldThrowWhenFileDoesNotExist(@TempDir Path tempDir) {
            Path nonExistent = tempDir.resolve("does-not-exist.fb2");

            assertThatThrownBy(() -> reader.read(nonExistent))
                    .isInstanceOf(FictionBookException.class);
        }
    }

    // ========================================================================
    // FORGIVING MODE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Forgiving Mode")
    class ForgivingModeTests {

        @Test
        @DisplayName("should skip unknown top-level elements")
        void shouldSkipUnknownTopLevelElements() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info><book-title>Test</book-title><lang>ru</lang></title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <custom-metadata>
                            <some-field>Some value</some-field>
                        </custom-metadata>
                        <body><section><p>Text</p></section></body>
                    </FictionBook>
                    """;

            FictionBookDto book = readFromString(xml);

            // Книга должна успешно прочитаться, игнорируя неизвестный тег
            assertThat(book).isNotNull();
            assertThat(book.description().titleInfo().bookTitle()).isEqualTo("Test");
        }

        @Test
        @DisplayName("should skip unknown elements inside body")
        void shouldSkipUnknownElementsInsideBody() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info><book-title>Test</book-title><lang>ru</lang></title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body>
                            <section>
                                <custom-tag>Ignored content</custom-tag>
                                <p>Valid paragraph</p>
                            </section>
                        </body>
                    </FictionBook>
                    """;

            FictionBookDto book = readFromString(xml);

            Section section = book.bodies().get(0).sections().get(0);
            // Должен остаться только валидный параграф
            assertThat(section.content()).hasSize(1);
            assertThat(section.content().get(0)).isInstanceOf(Paragraph.class);
        }
    }

    // ========================================================================
    // INPUT STREAM TESTS
    // ========================================================================

    @Nested
    @DisplayName("InputStream Reading")
    class InputStreamTests {

        @Test
        @DisplayName("should read from InputStream")
        void shouldReadFromInputStream() throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info><book-title>Stream Test</book-title><lang>en</lang></title-info>
                            <document-info><id>id</id><version>1.0</version></document-info>
                        </description>
                        <body><section><p>Stream content</p></section></body>
                    </FictionBook>
                    """;

            try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
                FictionBookDto book = reader.read(is);

                assertThat(book).isNotNull();
                assertThat(book.description().titleInfo().bookTitle()).isEqualTo("Stream Test");
            }
        }
    }

    // ========================================================================
    // ROUND-TRIP TESTS
    // ========================================================================

    @Nested
    @DisplayName("Round-trip")
    class RoundTripTests {

        @Test
        @DisplayName("should preserve data through multiple reads")
        void shouldPreserveDataThroughMultipleReads(@TempDir Path tempDir) throws Exception {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                        <description>
                            <title-info>
                                <genre>fiction</genre>
                                <author>
                                    <first-name>Иван</first-name>
                                    <last-name>Иванов</last-name>
                                </author>
                                <book-title>Тестовая книга</book-title>
                                <lang>ru</lang>
                            </title-info>
                            <document-info>
                                <id>round-trip-test</id>
                                <version>1.0</version>
                            </document-info>
                        </description>
                        <body>
                            <section id="ch1">
                                <title><p>Глава 1</p></title>
                                <p>Первый параграф</p>
                                <p>Второй параграф</p>
                            </section>
                        </body>
                    </FictionBook>
                    """;

            Path file = tempDir.resolve("roundtrip.fb2");
            Files.writeString(file, xml, StandardCharsets.UTF_8);

            // Первое чтение
            FictionBookDto book1 = reader.read(file);

            // Второе чтение того же файла
            FictionBookDto book2 = reader.read(file);

            // Данные должны совпадать
            assertThat(book2.description().titleInfo().bookTitle())
                    .isEqualTo(book1.description().titleInfo().bookTitle());
            assertThat(book2.description().titleInfo().authors())
                    .hasSize(book1.description().titleInfo().authors().size());
            assertThat(book2.bodies().get(0).sections())
                    .hasSize(book1.bodies().get(0).sections().size());
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Читает FB2 из строки через ByteArrayInputStream.
     */
    private FictionBookDto readFromString(String xml) throws Exception {
        try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            return reader.read(is);
        }
    }
}
