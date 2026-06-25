package org.tehlab.whitek0t.fictionbook.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.ResourceDataProvider;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.description.Author;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.dto.description.PublishInfo;
import org.tehlab.whitek0t.fictionbook.dto.description.Sequence;
import org.tehlab.whitek0t.fictionbook.dto.description.TitleInfo;
import org.tehlab.whitek0t.fictionbook.dto.inline.Link;
import org.tehlab.whitek0t.fictionbook.dto.inline.Strong;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты {@link BookInfo}: извлечение сводки из DTO, счётчики, обложка,
 * человекочитаемые жанры, ссылки и {@link BookInfo#toDisplayString()}.
 */
@DisplayName("BookInfo Tests")
class BookInfoTest {

    private FictionBookDto book(boolean withPublish, boolean withCover) {
        TitleInfo title = new TitleInfo(
                List.of(new Author("Юрий", null, "Винокуров"),
                        new Author("Олег", null, "Сапфир")),
                List.of("sf_litrpg"),
                "Кодекс Охотника",
                List.of(new Paragraph(List.of(new Text("Я был Охотником и чтил Кодекс.")))),
                "ru", null,
                new Sequence("Кодекс Охотника", 1),
                withCover ? List.of("cover.png") : List.of()
        );
        PublishInfo pub = withPublish
                ? new PublishInfo("Кодекс Охотника", "СамИздат", "Москва", "2022", "978-5-00")
                : null;
        Description desc = new Description(title, null, pub);

        Section sec = new Section("s1",
                List.of(new Paragraph(List.of(new Text("Глава")))),
                List.of(
                        new Paragraph(List.of(
                                new Text("Раз два "), new Strong(List.of(new Text("три"))), new Text(" четыре"))),
                        new Paragraph(List.of(
                                new Link("https://example.com", null, List.of(new Text("сайт")))))),
                List.of(), Map.of());
        BodyDto body = new BodyDto(null, List.of(sec));

        Map<String, Resource> res = new LinkedHashMap<>();
        if (withCover) {
            ResourceDataProvider p = () -> new ByteArrayInputStream(new byte[]{1, 2, 3});
            res.put("cover.png", new Resource("cover.png", "image/png", p));
        }
        return new FictionBookDto(desc, List.of(body), res);
    }

    @Nested
    @DisplayName("Извлечение полей")
    class Fields {

        @Test
        @DisplayName("метаданные, авторы, год, издательство")
        void basics() {
            BookInfo i = BookInfo.from(book(true, true), "1. Кодекс Охотника 1.fb2");
            assertThat(i.fileName()).isEqualTo("1. Кодекс Охотника 1.fb2");
            assertThat(i.title()).isEqualTo("Кодекс Охотника");
            assertThat(i.authorsLine()).isEqualTo("Юрий Винокуров, Олег Сапфир");
            assertThat(i.year()).isEqualTo("2022");
            assertThat(i.publisher()).isEqualTo("СамИздат");
            assertThat(i.city()).isEqualTo("Москва");
            assertThat(i.isbn()).isEqualTo("978-5-00");
            assertThat(i.sequence().name()).isEqualTo("Кодекс Охотника");
            assertThat(i.sequence().number()).isEqualTo(1);
        }

        @Test
        @DisplayName("без publish-info поля null, fileName null")
        void noPublishInfo() {
            BookInfo i = BookInfo.from(book(false, false));
            assertThat(i.fileName()).isNull();
            assertThat(i.year()).isNull();
            assertThat(i.publisher()).isNull();
            assertThat(i.isbn()).isNull();
        }

        @Test
        @DisplayName("счётчики символов и слов по тексту тела")
        void counters() {
            // Тело: "Глава" + "Раз два три четыре" + "сайт" → 6 слов (включая заголовок и текст ссылки)
            BookInfo i = BookInfo.from(book(false, false));
            assertThat(i.wordCount()).isEqualTo(6);
            assertThat(i.charCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("внешние ссылки собираются, внутренние якоря отфильтрованы")
        void links() {
            // Внешняя ссылка попадает; добавим внутренний якорь — он должен быть отброшен.
            Section secWithAnchor = new Section("s2", List.of(),
                    List.of(new Paragraph(List.of(
                            new Link("#footnote", null, List.of(new Text("сноска")))))),
                    List.of(), Map.of());
            FictionBookDto dto = book(false, false);
            FictionBookDto withAnchor = new FictionBookDto(
                    dto.description(),
                    List.of(new BodyDto(null, List.of(
                            dto.bodies().getFirst().sections().getFirst(), secWithAnchor))),
                    dto.resources());

            assertThat(BookInfo.from(withAnchor).links())
                    .containsExactly("https://example.com");
        }

        @Test
        @DisplayName("аннотация в плоском тексте")
        void annotation() {
            BookInfo i = BookInfo.from(book(false, false));
            assertThat(i.annotationText()).isEqualTo("Я был Охотником и чтил Кодекс.");
            assertThat(i.annotation()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Обложка и жанры")
    class CoverAndGenres {

        @Test
        @DisplayName("обложка резолвится из ресурсов")
        void coverResolved() {
            BookInfo i = BookInfo.from(book(false, true));
            assertThat(i.hasCover()).isTrue();
            assertThat(i.cover()).isNotNull();
            assertThat(i.cover().id()).isEqualTo("cover.png");
            assertThat(i.cover().contentType()).isEqualTo("image/png");
        }

        @Test
        @DisplayName("нет обложки → cover null")
        void noCover() {
            assertThat(BookInfo.from(book(false, false)).hasCover()).isFalse();
        }

        @Test
        @DisplayName("коды жанров переводятся в человекочитаемые")
        void genreNames() {
            assertThat(BookInfo.from(book(false, false)).genreNames())
                    .containsExactly("ЛитРПГ");
        }
    }

    @Nested
    @DisplayName("toDisplayString")
    class Display {

        @Test
        @DisplayName("содержит ключевые строки, пустые поля опущены")
        void display() {
            String out = BookInfo.from(book(true, true), "book.fb2").toDisplayString();
            assertThat(out)
                    .contains("Книга: \"Кодекс Охотника\"")
                    .contains("Имя файла: \"book.fb2\"")
                    .contains("Автор: Юрий Винокуров, Олег Сапфир")
                    .contains("Жанр: ЛитРПГ")
                    .contains("Цикл: Кодекс Охотника #1")
                    .contains("Год выхода: 2022")
                    .contains("Издательство: СамИздат")
                    .contains("Обложка: есть")
                    .contains("Описание: Я был Охотником")
                    .contains("Ссылки:")
                    .contains("- https://example.com");
        }

        @Test
        @DisplayName("без publish-info строки про издательство/год отсутствуют")
        void displayOmitsEmpty() {
            String out = BookInfo.from(book(false, false)).toDisplayString();
            assertThat(out)
                    .doesNotContain("Издательство:")
                    .doesNotContain("Год выхода:")
                    .doesNotContain("Имя файла:")
                    .contains("Обложка: нет");
        }
    }

    @Nested
    @DisplayName("Через FictionBookIO на реальном файле")
    class ViaFacade {

        @Test
        @DisplayName("FictionBookIO.info(Path) читает sample.fb2")
        void infoFromFile() throws Exception {
            Path sample = Path.of("src", "test", "resources", "books", "fb2", "sample.fb2");
            BookInfo i = FictionBookIO.info(sample);
            assertThat(i.fileName()).isEqualTo("sample.fb2");
            assertThat(i.title()).isEqualTo("Образцовый рассказ");
            assertThat(i.authorsLine()).isEqualTo("Антон Павлович Чехов");
            assertThat(i.genreNames()).containsExactly("Классическая проза");
            assertThat(i.hasCover()).isTrue();
            assertThat(i.wordCount()).isGreaterThan(0);
            // В sample.fb2 только внутренний якорь <a l:href="#ch2"> → внешних ссылок нет.
            assertThat(i.links()).isEmpty();

            // Глазами: распечатать сводку
            System.out.println("----- toDisplayString -----\n" + i.toDisplayString());
        }
    }
}
