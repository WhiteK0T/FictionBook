package org.tehlab.whitek0t.fictionbook.internal.reader.fb3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tehlab.whitek0t.fictionbook.api.FictionBookStreamer;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.dto.inline.ImageRef;
import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.internal.anchor.AnchorIndex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты потокового {@link Fb3Streamer}: ленивое чтение description и секций
 * (основное тело + сноски), eager-получение картинок по требованию, переписывание
 * ссылок {@code <img>} на якоря и построение индекса якорей.
 *
 * <p>Фикстура собирается на лету как валидный FB3/ZIP-архив (см. {@link #fb3Bytes}).</p>
 */
@DisplayName("Fb3Streamer Tests")
class Fb3StreamerTest {

    // ========================================================================
    // СБОРКА ФИКСТУРЫ
    // ========================================================================

    private static final String CONTENT_TYPES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Default Extension="png" ContentType="image/png"/>
              <Override PartName="/fb3/description.xml" ContentType="application/fb3-description+xml"/>
              <Override PartName="/fb3/body.xml" ContentType="application/fb3-body+xml"/>
              <Override PartName="/fb3/notes.xml" ContentType="application/fb3-body+xml"/>
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
              <Relationship Id="rId2" Type="http://www.fictionbook.org/FictionBook3/relationships/notes" Target="notes.xml"/>
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
              <title><main>Война и мир</main></title>
              <fb3-relations>
                <subject link="author" id="auth-1">
                  <first-name>Лев</first-name>
                  <last-name>Толстой</last-name>
                </subject>
              </fb3-relations>
              <lang>ru</lang>
            </fb3-description>
            """;

    private static final String BODY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <fb3-body xmlns="http://www.fictionbook.org/FictionBook3/body"
                     xmlns:l="http://www.w3.org/1999/xlink">
              <title><p>Война и мир</p></title>
              <section id="sec-1">
                <title><p>Глава первая</p></title>
                <p>Текст с картинкой: <img l:href="rId10"/></p>
                <section id="sec-1-1">
                  <title><p>Подглава</p></title>
                  <p>Вложенный параграф.</p>
                </section>
              </section>
              <section id="sec-2">
                <title><p>Глава вторая</p></title>
                <p>Ещё текст.</p>
              </section>
            </fb3-body>
            """;

    private static final String NOTES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <fb3-body xmlns="http://www.fictionbook.org/FictionBook3/body">
              <section id="note-1">
                <title><p>Примечание</p></title>
                <p>Текст сноски.</p>
              </section>
            </fb3-body>
            """;

    private byte[] fb3Bytes() {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        parts.put("[Content_Types].xml", CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
        parts.put("_rels/.rels", ROOT_RELS.getBytes(StandardCharsets.UTF_8));
        parts.put("fb3/_rels/description.xml.rels", DESCRIPTION_RELS.getBytes(StandardCharsets.UTF_8));
        parts.put("fb3/_rels/body.xml.rels", BODY_RELS.getBytes(StandardCharsets.UTF_8));
        parts.put("fb3/description.xml", DESCRIPTION.getBytes(StandardCharsets.UTF_8));
        parts.put("fb3/body.xml", BODY.getBytes(StandardCharsets.UTF_8));
        parts.put("fb3/notes.xml", NOTES.getBytes(StandardCharsets.UTF_8));
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

    @TempDir
    Path tempDir;

    private FictionBookStreamer open() throws Exception {
        Path file = tempDir.resolve("book.fb3");
        Files.write(file, fb3Bytes());
        return FictionBookStreamer.open(file);
    }

    // ========================================================================
    // ТЕСТЫ
    // ========================================================================

    @Nested
    @DisplayName("Описание книги")
    class DescriptionReading {

        @Test
        @DisplayName("readDescription возвращает метаданные")
        void readsDescription() throws Exception {
            try (FictionBookStreamer s = open()) {
                Description d = s.readDescription();
                assertThat(d).isNotNull();
                assertThat(d.titleInfo().bookTitle()).isEqualTo("Война и мир");
                assertThat(d.titleInfo().lang()).isEqualTo("ru");
            }
        }

        @Test
        @DisplayName("повторный вызов отдаёт тот же кэш")
        void readDescriptionIdempotent() throws Exception {
            try (FictionBookStreamer s = open()) {
                Description first = s.readDescription();
                Description second = s.readDescription();
                assertThat(second).isSameAs(first);
            }
        }
    }

    @Nested
    @DisplayName("Потоковое чтение секций")
    class SectionStreaming {

        @Test
        @DisplayName("секции основного тела и сносок отдаются по одной, затем null")
        void streamsTopLevelSections() throws Exception {
            try (FictionBookStreamer s = open()) {
                s.readDescription();

                List<String> ids = new ArrayList<>();
                Section sec;
                while ((sec = s.readNextSection()) != null) {
                    ids.add(sec.id());
                }
                // основное тело (sec-1, sec-2), затем тело сносок (note-1)
                assertThat(ids).containsExactly("sec-1", "sec-2", "note-1");
                assertThat(s.readNextSection()).isNull();
            }
        }

        @Test
        @DisplayName("вложенные секции остаются внутри родителя")
        void keepsNestedSections() throws Exception {
            try (FictionBookStreamer s = open()) {
                Section first = s.readNextSection();
                assertThat(first.id()).isEqualTo("sec-1");
                assertThat(first.subSections()).hasSize(1);
                assertThat(first.subSections().getFirst().id()).isEqualTo("sec-1-1");
            }
        }

        @Test
        @DisplayName("секции читаются и без предварительного readDescription")
        void streamsWithoutReadingDescriptionFirst() throws Exception {
            try (FictionBookStreamer s = open()) {
                Section first = s.readNextSection();
                assertThat(first).isNotNull();
                assertThat(first.id()).isEqualTo("sec-1");
            }
        }

        @Test
        @DisplayName("ссылка <img> переписывается на якорь #id")
        void rewritesImageHref() throws Exception {
            try (FictionBookStreamer s = open()) {
                Section first = s.readNextSection();
                ImageRef img = firstImage(first);
                assertThat(img).isNotNull();
                assertThat(img.href()).isEqualTo("#pic.png");
            }
        }

        private ImageRef firstImage(Section section) {
            for (var block : section.content()) {
                if (block instanceof Paragraph p) {
                    for (InlineElement e : p.elements()) {
                        if (e instanceof ImageRef img) return img;
                    }
                }
            }
            return null;
        }
    }

    @Nested
    @DisplayName("Бинарные ресурсы")
    class Resources {

        @Test
        @DisplayName("getResource резолвит по id и по #id")
        void resolvesById() throws Exception {
            try (FictionBookStreamer s = open()) {
                Resource cover = s.getResource("cover.png");
                assertThat(cover).isNotNull();
                assertThat(cover.contentType()).isEqualTo("image/png");
                assertThat(s.getResource("#pic.png")).isNotNull();
            }
        }

        @Test
        @DisplayName("неизвестный id → null")
        void unknownIdReturnsNull() throws Exception {
            try (FictionBookStreamer s = open()) {
                assertThat(s.getResource("missing.png")).isNull();
                assertThat(s.getResource(null)).isNull();
            }
        }

        @Test
        @DisplayName("ресурсы доступны независимо от позиции стрима секций")
        void resourcesIndependentOfSectionCursor() throws Exception {
            try (FictionBookStreamer s = open()) {
                while (s.readNextSection() != null) {
                    // drain
                }
                assertThat(s.getResource("cover.png")).isNotNull();
            }
        }

        @Test
        @DisplayName("байты картинки читаются из архива по требованию (корректны)")
        void resourceBytesAreLazilyCorrect() throws Exception {
            try (FictionBookStreamer s = open()) {
                Resource pic = s.getResource("pic.png");
                assertThat(pic).isNotNull();
                byte[] data;
                try (InputStream in = pic.dataProvider().getInputStream()) {
                    data = in.readAllBytes();
                }
                // Содержимое fb3/img/pic.png из фикстуры.
                assertThat(data).isEqualTo(new byte[]{(byte) 0x89, 'P', 'N', 'G', 4, 5, 6});
            }
        }

        @Test
        @DisplayName("после close() ресурс больше не читается (поток шёл из открытого ZIP)")
        void resourceUnreadableAfterClose() throws Exception {
            FictionBookStreamer s = open();
            Resource pic = s.getResource("pic.png");
            s.close();
            // Провайдер тянет байты из ZipFile, который закрыт в close() — доказательство
            // ленивости (eager-копия в памяти читалась бы и после закрытия).
            assertThatThrownBy(() -> {
                try (InputStream in = pic.dataProvider().getInputStream()) {
                    in.readAllBytes();
                }
            }).isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("Индекс якорей")
    class Anchors {

        @Test
        @DisplayName("buildAnchorIndex находит секционные якоря")
        void buildsAnchorIndex() throws Exception {
            try (FictionBookStreamer s = open()) {
                AnchorIndex index = s.buildAnchorIndex();
                assertThat(index.contains("sec-2")).isTrue();
                assertThat(index.canResolve("#sec-2")).isTrue();
            }
        }
    }
}
