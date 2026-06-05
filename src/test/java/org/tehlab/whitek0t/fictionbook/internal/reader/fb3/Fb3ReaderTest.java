package org.tehlab.whitek0t.fictionbook.internal.reader.fb3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tehlab.whitek0t.fictionbook.api.FictionBookFormat;
import org.tehlab.whitek0t.fictionbook.api.FictionBookIO;
import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.description.Author;
import org.tehlab.whitek0t.fictionbook.dto.inline.ImageRef;
import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Strong;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.exception.InvalidFormatException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты {@link Fb3Reader}: распаковка OPC-контейнера, навигация по {@code .rels},
 * разбор {@code description.xml}/{@code body.xml}, разрешение картинок.
 *
 * <p>Фикстуры собираются на лету как валидные ZIP/FB3-архивы (см. {@link #fb3Bytes}).</p>
 */
@DisplayName("Fb3Reader Tests")
class Fb3ReaderTest {

    // ========================================================================
    // СБОРКА ФИКСТУР
    // ========================================================================

    private static final String CONTENT_TYPES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Default Extension="png" ContentType="image/png"/>
              <Override PartName="/fb3/description.xml" ContentType="application/fb3-description+xml"/>
              <Override PartName="/fb3/body.xml" ContentType="application/fb3-body+xml"/>
            </Types>
            """;

    private static final String ROOT_RELS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://www.fictionbook.org/FictionBook3/relationships/Book" Target="fb3/description.xml"/>
            </Relationships>
            """;

    private static final String DESCRIPTION_RELS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://www.fictionbook.org/FictionBook3/relationships/body" Target="body.xml"/>
              <Relationship Id="rIdCover" Type="http://www.fictionbook.org/FictionBook3/relationships/cover" Target="img/cover.png"/>
            </Relationships>
            """;

    private static final String BODY_RELS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId10" Type="http://www.fictionbook.org/FictionBook3/relationships/image" Target="img/pic.png"/>
            </Relationships>
            """;

    private static final String DESCRIPTION = """
            <?xml version="1.0" encoding="UTF-8"?>
            <fb3-description xmlns="http://www.fictionbook.org/FictionBook3/description"
                            id="11111111-2222-3333-4444-555555555555" version="1.0">
              <title><main>Война и мир</main><sub>Том первый</sub></title>
              <fb3-relations>
                <subject link="author" id="auth-1">
                  <title><main>Лев Толстой</main></title>
                  <first-name>Лев</first-name>
                  <middle-name>Николаевич</middle-name>
                  <last-name>Толстой</last-name>
                </subject>
              </fb3-relations>
              <fb3-classification>
                <subject>prose_classic</subject>
              </fb3-classification>
              <lang>ru</lang>
              <sequence name="Классика" number="3"/>
              <annotation>
                <p>Краткое <strong>описание</strong> книги.</p>
              </annotation>
            </fb3-description>
            """;

    private static final String BODY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <fb3-body xmlns="http://www.fictionbook.org/FictionBook3/body"
                     xmlns:l="http://www.w3.org/1999/xlink">
              <title><p>Война и мир</p></title>
              <section id="sec-1">
                <title><p>Глава первая</p></title>
                <p>Обычный текст с <strong>жирным</strong> и <emphasis>курсивом</emphasis>.</p>
                <p>Картинка по связи: <img l:href="rId10"/></p>
                <p>Картинка по пути: <img l:href="img/pic.png"/></p>
                <blockquote><p>Цитата.</p></blockquote>
                <section id="sec-1-1">
                  <title><p>Подглава</p></title>
                  <p>Вложенный параграф.</p>
                </section>
              </section>
            </fb3-body>
            """;

    /** Собирает FB3-архив из стандартного набора частей. */
    private byte[] fb3Bytes() {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        parts.put("[Content_Types].xml", CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
        parts.put("_rels/.rels", ROOT_RELS.getBytes(StandardCharsets.UTF_8));
        parts.put("fb3/_rels/description.xml.rels", DESCRIPTION_RELS.getBytes(StandardCharsets.UTF_8));
        parts.put("fb3/_rels/body.xml.rels", BODY_RELS.getBytes(StandardCharsets.UTF_8));
        parts.put("fb3/description.xml", DESCRIPTION.getBytes(StandardCharsets.UTF_8));
        parts.put("fb3/body.xml", BODY.getBytes(StandardCharsets.UTF_8));
        parts.put("fb3/img/cover.png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});
        parts.put("fb3/img/pic.png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 4, 5, 6});
        return zip(parts);
    }

    private static byte[] zip(Map<String, byte[]> parts) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (var e : parts.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return baos.toByteArray();
    }

    private FictionBookDto read() throws FictionBookException {
        return new Fb3Reader().read(new ByteArrayInputStream(fb3Bytes()));
    }

    // ========================================================================
    // ТЕСТЫ
    // ========================================================================

    @Nested
    @DisplayName("Description")
    class DescriptionParsing {

        @Test
        @DisplayName("читает название, язык, id и версию")
        void readsTitleLangIdVersion() throws Exception {
            var d = read().description();
            assertThat(d.titleInfo().bookTitle()).isEqualTo("Война и мир");
            assertThat(d.titleInfo().lang()).isEqualTo("ru");
            assertThat(d.documentInfo().id()).isEqualTo("11111111-2222-3333-4444-555555555555");
            assertThat(d.documentInfo().version()).isEqualTo("1.0");
        }

        @Test
        @DisplayName("читает автора из fb3-relations")
        void readsAuthor() throws Exception {
            var authors = read().description().titleInfo().authors();
            assertThat(authors).hasSize(1);
            Author a = authors.get(0);
            assertThat(a.firstName()).isEqualTo("Лев");
            assertThat(a.middleName()).isEqualTo("Николаевич");
            assertThat(a.lastName()).isEqualTo("Толстой");
        }

        @Test
        @DisplayName("читает жанр, серию и аннотацию")
        void readsGenreSequenceAnnotation() throws Exception {
            var ti = read().description().titleInfo();
            assertThat(ti.genres()).containsExactly("prose_classic");
            assertThat(ti.sequence()).isNotNull();
            assertThat(ti.sequence().name()).isEqualTo("Классика");
            assertThat(ti.sequence().number()).isEqualTo(3);
            assertThat(ti.annotation()).isNotEmpty();
        }

        @Test
        @DisplayName("обложка попадает в coverImageIds")
        void readsCover() throws Exception {
            assertThat(read().description().titleInfo().coverImageIds())
                    .containsExactly("cover.png");
        }
    }

    @Nested
    @DisplayName("Body")
    class BodyParsing {

        @Test
        @DisplayName("читает секции с заголовком и вложенностью")
        void readsSections() throws Exception {
            FictionBookDto dto = read();
            assertThat(dto.bodies()).hasSize(1);
            BodyDto body = dto.bodies().get(0);
            assertThat(body.sections()).hasSize(1);

            Section sec = body.sections().get(0);
            assertThat(sec.id()).isEqualTo("sec-1");
            assertThat(sec.title()).hasSize(1);
            assertThat(sec.subSections()).hasSize(1);
            assertThat(sec.subSections().get(0).id()).isEqualTo("sec-1-1");
        }

        @Test
        @DisplayName("читает inline-форматирование (strong/emphasis)")
        void readsInlineFormatting() throws Exception {
            Section sec = read().bodies().get(0).sections().get(0);
            Paragraph first = (Paragraph) sec.content().get(0);
            boolean hasStrong = first.elements().stream().anyMatch(e -> e instanceof Strong);
            assertThat(hasStrong).isTrue();
        }

        @Test
        @DisplayName("blockquote разбирается как блок (cite-алиас)")
        void readsBlockquote() throws Exception {
            Section sec = read().bodies().get(0).sections().get(0);
            // p, p(img), p(img), blockquote → cite присутствует
            boolean hasCite = sec.content().stream()
                    .anyMatch(b -> b instanceof org.tehlab.whitek0t.fictionbook.dto.block.Cite);
            assertThat(hasCite).isTrue();
        }
    }

    @Nested
    @DisplayName("Images")
    class Images {

        @Test
        @DisplayName("картинки регистрируются как ресурсы")
        void registersResources() throws Exception {
            var resources = read().resources();
            assertThat(resources).containsKeys("cover.png", "pic.png");
            assertThat(resources.get("pic.png").contentType()).isEqualTo("image/png");
        }

        @Test
        @DisplayName("ссылка по Id связи переписывается в #id")
        void rewritesRelationshipHref() throws Exception {
            Section sec = read().bodies().get(0).sections().get(0);
            ImageRef img = findImage(sec.content().get(1));
            assertThat(img).isNotNull();
            assertThat(img.href()).isEqualTo("#pic.png");
        }

        @Test
        @DisplayName("прямая ссылка по пути переписывается в #id")
        void rewritesPathHref() throws Exception {
            Section sec = read().bodies().get(0).sections().get(0);
            ImageRef img = findImage(sec.content().get(2));
            assertThat(img).isNotNull();
            assertThat(img.href()).isEqualTo("#pic.png");
        }

        private ImageRef findImage(Object block) {
            if (block instanceof Paragraph p) {
                for (InlineElement e : p.elements()) {
                    if (e instanceof ImageRef img) return img;
                }
            }
            return null;
        }
    }

    @Nested
    @DisplayName("API & errors")
    class ApiAndErrors {

        @Test
        @DisplayName("FictionBookIO определяет FB3 по magic bytes и читает")
        void detectsAndReadsViaFacade(@TempDir Path dir) throws Exception {
            Path file = dir.resolve("book.fb3");
            Files.write(file, fb3Bytes());

            assertThat(FictionBookFormat.detect(file)).isEqualTo(FictionBookFormat.FB3);
            FictionBookDto dto = FictionBookIO.read(file);
            assertThat(dto.description().titleInfo().bookTitle()).isEqualTo("Война и мир");
        }

        @Test
        @DisplayName("повреждённый архив → InvalidFormatException")
        void brokenArchive() {
            byte[] notZip = "this is not a zip".getBytes(StandardCharsets.UTF_8);
            assertThatThrownBy(() -> new Fb3Reader().read(new ByteArrayInputStream(notZip)))
                    .isInstanceOf(InvalidFormatException.class);
        }

        @Test
        @DisplayName("отсутствует body.xml → InvalidFormatException")
        void missingBody() {
            Map<String, byte[]> parts = new LinkedHashMap<>();
            parts.put("[Content_Types].xml", CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
            parts.put("_rels/.rels", ROOT_RELS.getBytes(StandardCharsets.UTF_8));
            parts.put("fb3/description.xml", DESCRIPTION.getBytes(StandardCharsets.UTF_8));
            // body.xml и его .rels намеренно отсутствуют
            byte[] archive = zip(parts);

            assertThatThrownBy(() -> new Fb3Reader().read(new ByteArrayInputStream(archive)))
                    .isInstanceOf(InvalidFormatException.class);
        }
    }
}
