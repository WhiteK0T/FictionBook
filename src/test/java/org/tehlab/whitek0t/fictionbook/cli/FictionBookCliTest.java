package org.tehlab.whitek0t.fictionbook.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты консольной утилиты {@link FictionBookCli}: разбор аргументов,
 * вывод в txt/html, режимы картинок и коды возврата.
 */
@DisplayName("FictionBookCli Tests")
class FictionBookCliTest {

    private static final Path SAMPLE =
            Path.of("src", "test", "resources", "books", "fb2", "sample.fb2");

    /** Вызывает package-private {@code run} через рефлексию (метод не публичный). */
    private static Result invoke(String... args) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Method run = FictionBookCli.class.getDeclaredMethod(
                "run", String[].class, PrintStream.class, PrintStream.class);
        run.setAccessible(true);
        int code = (int) run.invoke(null, args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code,
                out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int code, String out, String err) {
    }

    /**
     * Минимальная FB2 с <em>инлайновой</em> картинкой внутри {@code <p>}.
     * Блочные {@code <image>} (как в sample.fb2) библиотека пока не рендерит,
     * поэтому для проверки картинок CLI используем инлайновую разметку.
     */
    private static Path inlineImageBook(Path dir) throws Exception {
        // 1x1 PNG
        String png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk"
                + "+M8AAAMBAQDJ/pLvAAAAAElFTkSuQmCC";
        String fb2 = """
                <?xml version="1.0" encoding="UTF-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" \
                xmlns:l="http://www.w3.org/1999/xlink">
                  <description>
                    <title-info>
                      <genre>prose</genre>
                      <book-title>Книга с картинкой</book-title>
                      <lang>ru</lang>
                    </title-info>
                  </description>
                  <body>
                    <section id="s1">
                      <p>До картинки <image l:href="#img1.png"/> после картинки.</p>
                    </section>
                  </body>
                  <binary id="img1.png" content-type="image/png">%s</binary>
                </FictionBook>
                """.formatted(png);
        Path file = dir.resolve("inline.fb2");
        Files.writeString(file, fb2, StandardCharsets.UTF_8);
        return file;
    }

    @Nested
    @DisplayName("Аргументы")
    class Arguments {

        @Test
        @DisplayName("--help печатает справку и возвращает 0")
        void help() throws Exception {
            Result r = invoke("--help");
            assertThat(r.code()).isZero();
            assertThat(r.out()).contains("Использование", "--format", "--images");
        }

        @Test
        @DisplayName("без входного файла → код 2")
        void missingInput() throws Exception {
            Result r = invoke();
            assertThat(r.code()).isEqualTo(2);
            assertThat(r.err()).contains("Не указан входной файл");
        }

        @Test
        @DisplayName("несуществующий файл → код 1")
        void missingFile() throws Exception {
            Result r = invoke("no-such-book.fb2");
            assertThat(r.code()).isEqualTo(1);
            assertThat(r.err()).contains("Файл не найден");
        }

        @Test
        @DisplayName("неизвестный формат → код 2")
        void badFormat() throws Exception {
            Result r = invoke(SAMPLE.toString(), "-f", "pdf");
            assertThat(r.code()).isEqualTo(2);
            assertThat(r.err()).contains("Неизвестный формат");
        }
    }

    @Nested
    @DisplayName("Конвертация в txt")
    class ToText {

        @Test
        @DisplayName("в stdout с дефолтным форматом txt")
        void stdout() throws Exception {
            Result r = invoke(SAMPLE.toString(), "-o", "-");
            assertThat(r.code()).isZero();
            // Шапка из метаданных (название) + тело книги.
            assertThat(r.out()).contains(
                    "Образцовый рассказ", "Глава первая", "Заключительный абзац рассказа");
            // В stdout не должно быть HTML-тегов.
            assertThat(r.out()).doesNotContain("<p>", "<section");
        }

        @Test
        @DisplayName("в файл с производным именем")
        void toFile(@TempDir Path dir) throws Exception {
            Path input = dir.resolve("book.fb2");
            Files.copy(SAMPLE, input);

            Result r = invoke(input.toString());
            assertThat(r.code()).isZero();

            Path expected = dir.resolve("book.txt");
            assertThat(expected).exists();
            assertThat(Files.readString(expected)).contains("Глава первая");
        }
    }

    @Nested
    @DisplayName("Конвертация в html")
    class ToHtml {

        @Test
        @DisplayName("формат выводится из расширения выходного файла")
        void inferredFromOutput(@TempDir Path dir) throws Exception {
            Path out = dir.resolve("book.html");
            Result r = invoke(SAMPLE.toString(), "-o", out.toString());

            assertThat(r.code()).isZero();
            String html = Files.readString(out);
            assertThat(html).contains("<!DOCTYPE html>", "<title>Образцовый рассказ</title>");
        }

        @Test
        @DisplayName("--no-wrap отдаёт фрагмент без <html>")
        void fragment(@TempDir Path dir) throws Exception {
            Path out = dir.resolve("frag.html");
            Result r = invoke(SAMPLE.toString(), "-o", out.toString(), "--no-wrap");

            assertThat(r.code()).isZero();
            String html = Files.readString(out);
            assertThat(html).doesNotContain("<!DOCTYPE html>");
            assertThat(html).contains("<section");
        }

        @Test
        @DisplayName("--images extract складывает картинки в папку <имя>_files")
        void extractImages(@TempDir Path dir) throws Exception {
            Path input = inlineImageBook(dir);
            Path out = dir.resolve("book.html");
            Result r = invoke(input.toString(), "-f", "html",
                    "-o", out.toString(), "--images", "extract");

            assertThat(r.code()).isZero();
            Path imagesDir = dir.resolve("book_files");
            assertThat(imagesDir).isDirectory();
            assertThat(Files.list(imagesDir)).isNotEmpty();
            // В HTML ссылки на извлечённые файлы, а не base64.
            assertThat(Files.readString(out)).contains("book_files/");
        }

        @Test
        @DisplayName("--images embed встраивает картинки в base64")
        void embedImages(@TempDir Path dir) throws Exception {
            Path input = inlineImageBook(dir);
            Path out = dir.resolve("book.html");
            Result r = invoke(input.toString(), "-f", "html",
                    "-o", out.toString(), "--images", "embed");

            assertThat(r.code()).isZero();
            assertThat(Files.readString(out)).contains("data:image/");
        }
    }

    @Nested
    @DisplayName("Шапка из метаданных")
    class FrontMatter {

        @Test
        @DisplayName("txt: название, автор и аннотация попадают в вывод")
        void txtIncludesTitleAuthorAnnotation() throws Exception {
            Result r = invoke(SAMPLE.toString(), "-o", "-");
            assertThat(r.code()).isZero();
            // Метаданные из <description> (sample.fb2): book-title, автор, аннотация.
            assertThat(r.out()).contains(
                    "Образцовый рассказ",   // book-title
                    "Чехов",                // автор
                    "аннотация");           // текст <annotation>
        }

        @Test
        @DisplayName("html: обложка из <coverpage> рендерится как <img>")
        void htmlIncludesCoverAndTitle(@TempDir Path dir) throws Exception {
            Path out = dir.resolve("book.html");
            Result r = invoke(SAMPLE.toString(), "-o", out.toString());

            assertThat(r.code()).isZero();
            String html = Files.readString(out);
            // Заголовок книги виден в теле (а не только в <title>) и есть обложка.
            assertThat(html).contains(
                    "class=\"section-title\"",
                    "<img src=\"data:image/png;base64,");
        }
    }
}
