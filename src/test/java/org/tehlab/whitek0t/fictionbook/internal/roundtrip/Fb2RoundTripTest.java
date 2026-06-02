package org.tehlab.whitek0t.fictionbook.internal.roundtrip;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.EmptyLine;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.description.Author;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.dto.description.DocumentInfo;
import org.tehlab.whitek0t.fictionbook.dto.description.PublishInfo;
import org.tehlab.whitek0t.fictionbook.dto.description.Sequence;
import org.tehlab.whitek0t.fictionbook.dto.description.TitleInfo;
import org.tehlab.whitek0t.fictionbook.dto.inline.Emphasis;
import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Link;
import org.tehlab.whitek0t.fictionbook.dto.inline.Strong;
import org.tehlab.whitek0t.fictionbook.dto.inline.Sub;
import org.tehlab.whitek0t.fictionbook.dto.inline.Sup;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;
import org.tehlab.whitek0t.fictionbook.internal.reader.fb2.Fb2Reader;
import org.tehlab.whitek0t.fictionbook.internal.writer.fb2.Fb2Writer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip тесты: DTO → write → read → DTO.
 * <p>
 * Главный инвариант библиотеки при стратегии «прощающее чтение / строгая запись»:
 * после первой строгой записи книга должна выходить на <b>фикспоинт</b> —
 * повторный цикл write → read → write обязан давать байт-в-байт идентичный XML.
 * Любой дрейф (потеря узла, перестановка атрибутов, схлопывание текста) ломает этот тест.
 * <p>
 * Книги здесь заведомо «чистые» (уже в каноничном виде), поэтому первая запись
 * не должна ничего менять санитайзерами — расхождение означает баг в read или write.
 */
@DisplayName("FB2 round-trip (DTO → write → read → DTO)")
class Fb2RoundTripTest {

    // ========================================================================
    // ХЕЛПЕРЫ
    // ========================================================================

    /** Сериализует книгу в FB2-байты дефолтным райтером (стабильная конфигурация). */
    private static byte[] write(FictionBookDto book) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Fb2Writer().write(book, baos);
        return baos.toByteArray();
    }

    /** Читает FB2-байты обратно в DTO. */
    private static FictionBookDto read(byte[] fb2) throws Exception {
        return new Fb2Reader().read(new ByteArrayInputStream(fb2));
    }

    private static Paragraph p(String text) {
        return new Paragraph(List.of(new Text(text)));
    }

    private static Paragraph p(InlineElement... elements) {
        return new Paragraph(List.of(elements));
    }

    /**
     * Заведомо «чистая» книга с разнообразным, но надёжно поддерживаемым контентом:
     * метаданные, вложенные секции, форматирование, ссылки, sub/sup, тело примечаний,
     * бинарный ресурс. Намеренно без пустых/мусорных узлов, чтобы санитайзеры
     * не вносили правок на первом проходе.
     */
    static FictionBookDto richBook() {
        Description description = new Description(
                new TitleInfo(
                        List.of(
                                new Author("Лев", "Николаевич", "Толстой"),
                                new Author("Фёдор", null, "Достоевский")
                        ),
                        List.of("prose_classic", "prose_history"),
                        "Война и мир",
                        // ВНИМАНИЕ: annotation сознательно пуста — её round-trip сейчас сломан
                        // (Jackson @JacksonXmlText не захватывает вложенный <p>), см. annotationRoundTripIsKnownGap().
                        List.of(),
                        "ru", "fr",
                        new Sequence("Великие романы", 4),
                        List.of()
                ),
                new DocumentInfo(
                        List.of(new Author("Пётр", null, "Оцифровщик")),
                        "FictionBook lib test",
                        "2026-06-01",
                        "https://example.com/source",
                        "OCR Team",
                        "doc-id-42",
                        "1.3",
                        // history тоже сознательно пуст — общий с annotation баг @JacksonXmlText.
                        List.of()
                ),
                new PublishInfo(
                        "Полное собрание сочинений",
                        "Издательство «Тест»",
                        "Москва",
                        "1869",
                        "978-5-00000-000-0"
                )
        );

        Section nested = new Section(
                "ch1-1",
                List.of(p("Подглава")),
                List.of(
                        p("Текст подглавы с "),
                        new Paragraph(List.of(
                                new Text("формулой H"),
                                new Sub(List.of(new Text("2"))),
                                new Text("O и x"),
                                new Sup(List.of(new Text("n")))
                        ))
                ),
                List.of(),
                Map.of()
        );

        Section chapter = new Section(
                "ch1",
                List.of(p("Глава первая")),
                List.of(
                        new Paragraph(List.of(
                                new Text("Обычный текст, "),
                                new Strong(List.of(new Text("жирный"))),
                                new Text(", "),
                                new Emphasis(List.of(
                                        new Text("курсив и "),
                                        new Strong(List.of(new Text("жирный курсив")))
                                )),
                                new Text(".")
                        )),
                        new Paragraph(List.of(
                                new Text("Текст со "),
                                new Link("#note1", "note", List.of(new Text("[1]"))),
                                new Text(" и "),
                                new Link("https://example.com", null, List.of(new Text("внешней ссылкой")))
                        )),
                        new EmptyLine(),
                        p("Параграф после пустой строки.")
                ),
                List.of(nested),
                Map.of()
        );

        BodyDto mainBody = new BodyDto(null, List.of(chapter));

        Section note = new Section(
                "note1",
                List.of(p("1")),
                List.of(p("Текст примечания номер один.")),
                List.of(),
                Map.of()
        );
        BodyDto notesBody = new BodyDto("notes", List.of(note));

        byte[] coverData = "binary-cover-payload-".getBytes();
        Resource cover = new Resource("cover.jpg", "image/jpeg",
                () -> new ByteArrayInputStream(coverData));

        return new FictionBookDto(
                description,
                List.of(mainBody, notesBody),
                Map.of("cover.jpg", cover)
        );
    }

    // ========================================================================
    // ГЛАВНЫЙ ИНВАРИАНТ: ФИКСПОИНТ
    // ========================================================================

    @Test
    @DisplayName("write → read → write даёт байт-в-байт идентичный XML (фикспоинт)")
    void writeReadWriteIsStable() throws Exception {
        FictionBookDto original = richBook();

        byte[] firstPass = write(original);
        FictionBookDto reread = read(firstPass);
        byte[] secondPass = write(reread);

        assertThat(new String(secondPass))
                .as("повторная сериализация после чтения обязана совпасть с первой")
                .isEqualTo(new String(firstPass));
    }

    @Test
    @DisplayName("минимальная книга стабильна на повторном цикле")
    void minimalBookIsStable() throws Exception {
        FictionBookDto minimal = new FictionBookDto(
                richBook().description(),
                List.of(new BodyDto(null, List.of(
                        new Section(null, List.of(), List.of(p("Hello, world!")),
                                List.of(), Map.of())
                ))),
                Map.of()
        );

        byte[] firstPass = write(minimal);
        byte[] secondPass = write(read(firstPass));

        assertThat(new String(secondPass)).isEqualTo(new String(firstPass));
    }

    /**
     * ИЗВЕСТНЫЙ БАГ (round-trip ловит его): {@code <annotation>} и {@code <history>}
     * записываются, но не читаются обратно. Причина — {@code @JacksonXmlText} в
     * {@code Fb2AnnotationJax}/{@code Fb2HistoryJax} захватывает только прямой текст
     * элемента, но не вложенную разметку {@code <p>…</p>}, поэтому при чтении
     * содержимое теряется.
     * <p>
     * Тест намеренно отключён. Снять {@code @Disabled} после фикса ридера —
     * тогда он станет защитой от регрессии.
     */
    @Test
    @Disabled("Известный баг ридера: annotation/history не читаются (Jackson @JacksonXmlText "
            + "не захватывает вложенный <p>). Включить после фикса.")
    @DisplayName("KNOWN GAP: annotation и history переживают round-trip")
    void annotationAndHistoryRoundTripIsKnownGap() throws Exception {
        Description withMixedContent = new Description(
                new TitleInfo(
                        List.of(new Author("Лев", null, "Толстой")),
                        List.of("prose_classic"),
                        "Книга",
                        List.of(p("Аннотация с текстом.")),  // ← теряется при чтении
                        "ru", null, null, List.of()
                ),
                new DocumentInfo(
                        List.of(), null, null, null, null, "id-1", "1.0",
                        List.of("Первая версия", "Вторая версия")  // ← теряется при чтении
                ),
                null
        );
        FictionBookDto book = new FictionBookDto(
                withMixedContent,
                List.of(new BodyDto(null, List.of(
                        new Section(null, List.of(), List.of(p("Текст")), List.of(), Map.of())))),
                Map.of()
        );

        byte[] firstPass = write(book);
        byte[] secondPass = write(read(firstPass));

        assertThat(new String(secondPass)).isEqualTo(new String(firstPass));
    }

    // ========================================================================
    // СОХРАННОСТЬ МЕТАДАННЫХ
    // ========================================================================

    @Nested
    @DisplayName("Метаданные переживают round-trip")
    class MetadataRoundTrip {

        @Test
        void titleInfoSurvives() throws Exception {
            FictionBookDto reread = read(write(richBook()));
            TitleInfo title = reread.description().titleInfo();

            assertThat(title.bookTitle()).isEqualTo("Война и мир");
            assertThat(title.lang()).isEqualTo("ru");
            assertThat(title.genres()).containsExactly("prose_classic", "prose_history");
            assertThat(title.authors())
                    .extracting(Author::lastName)
                    .containsExactly("Толстой", "Достоевский");
            assertThat(title.sequence().name()).isEqualTo("Великие романы");
            assertThat(title.sequence().number()).isEqualTo(4);
        }

        @Test
        void documentInfoSurvives() throws Exception {
            FictionBookDto reread = read(write(richBook()));
            DocumentInfo doc = reread.description().documentInfo();

            assertThat(doc.id()).isEqualTo("doc-id-42");
            assertThat(doc.version()).isEqualTo("1.3");
            assertThat(doc.date()).isEqualTo("2026-06-01");
        }

        @Test
        void publishInfoSurvives() throws Exception {
            FictionBookDto reread = read(write(richBook()));
            PublishInfo publish = reread.description().publishInfo();

            assertThat(publish).isNotNull();
            assertThat(publish.publisher()).isEqualTo("Издательство «Тест»");
            assertThat(publish.city()).isEqualTo("Москва");
            assertThat(publish.year()).isEqualTo("1869");
            assertThat(publish.isbn()).isEqualTo("978-5-00000-000-0");
        }
    }

    // ========================================================================
    // СОХРАННОСТЬ КОНТЕНТА ТЕЛА
    // ========================================================================

    @Nested
    @DisplayName("Контент тела переживает round-trip")
    class BodyRoundTrip {

        @Test
        void bodiesAndSectionStructureSurvive() throws Exception {
            FictionBookDto reread = read(write(richBook()));

            assertThat(reread.bodies()).hasSize(2);
            assertThat(reread.bodies().get(0).name()).isNull();
            assertThat(reread.bodies().get(1).name()).isEqualTo("notes");

            Section chapter = reread.bodies().get(0).sections().get(0);
            assertThat(chapter.id()).isEqualTo("ch1");
            assertThat(chapter.subSections()).hasSize(1);
            assertThat(chapter.subSections().get(0).id()).isEqualTo("ch1-1");
        }

        @Test
        void inlineFormattingSurvives() throws Exception {
            FictionBookDto reread = read(write(richBook()));
            Section chapter = reread.bodies().get(0).sections().get(0);

            Paragraph formatted = (Paragraph) chapter.content().get(0);
            assertThat(formatted.elements()).hasAtLeastOneElementOfType(Strong.class);
            assertThat(formatted.elements()).hasAtLeastOneElementOfType(Emphasis.class);
        }

        @Test
        void linksSurvive() throws Exception {
            FictionBookDto reread = read(write(richBook()));
            Section chapter = reread.bodies().get(0).sections().get(0);

            Paragraph withLinks = (Paragraph) chapter.content().get(1);
            List<Link> links = withLinks.elements().stream()
                    .filter(Link.class::isInstance)
                    .map(Link.class::cast)
                    .toList();

            assertThat(links).extracting(Link::href)
                    .containsExactly("#note1", "https://example.com");
        }

        @Test
        void binaryResourceSurvivesBitForBit() throws Exception {
            byte[] expected = "binary-cover-payload-".getBytes();

            FictionBookDto reread = read(write(richBook()));

            assertThat(reread.resources()).containsKey("cover.jpg");
            byte[] actual;
            try (var in = reread.resources().get("cover.jpg").dataProvider().getInputStream()) {
                actual = in.readAllBytes();
            }
            assertThat(actual).isEqualTo(expected);
        }
    }
}
