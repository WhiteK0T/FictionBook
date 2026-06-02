package org.tehlab.whitek0t.fictionbook.internal.writer.fb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tehlab.whitek0t.fictionbook.api.FictionBookIO;
import org.tehlab.whitek0t.fictionbook.dto.*;
import org.tehlab.whitek0t.fictionbook.dto.block.*;
import org.tehlab.whitek0t.fictionbook.dto.description.*;
import org.tehlab.whitek0t.fictionbook.dto.inline.*;
import org.tehlab.whitek0t.fictionbook.internal.sanitizer.SanitizerPipeline;
import org.tehlab.whitek0t.fictionbook.internal.sanitizer.TextNodeMerger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Fb2WriterTest {

    @TempDir
    Path tempDir;

    // ========================================================================
    // ТЕСТЫ
    // ========================================================================

    @Test
    void shouldWriteMinimalBook() throws Exception {
        FictionBookDto book = createMinimalBook();
        Path output = tempDir.resolve("minimal.fb2");

        Fb2Writer writer = new Fb2Writer();
        writer.write(book, output);

        String xml = Files.readString(output);

        // Гибкая проверка XML declaration (StAX может использовать ' или ")
        assertThat(xml).containsPattern("<\\?xml\\s+version=['\"]1\\.0['\"]\\s+encoding=['\"]UTF-8['\"]\\?>");
        assertThat(xml).contains("<FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\"");
        assertThat(xml).contains("<description>");
        assertThat(xml).contains("<body>");
        assertThat(xml).contains("<book-title>Test Book</book-title>");
        assertThat(xml).contains("<p>Hello, world!</p>");
    }

    @Test
    void shouldWriteDescription() throws Exception {
        Description desc = new Description(
                new TitleInfo(
                        List.of(new Author("Иван", "Иванович", "Иванов")),
                        List.of("fiction", "fantasy"),
                        "Тестовая книга",
                        List.of(new Paragraph(List.of(new Text("Аннотация книги")))),
                        "ru", "en",
                        new Sequence("Серия книг", 1),
                        List.of("cover")
                ),
                new DocumentInfo(
                        List.of(new Author("Пётр", null, "Петров")),
                        "Custom OCR Program",
                        "2024-01-15",
                        "http://example.com/source",
                        "OCR Author",
                        "unique-id-123",
                        "2.5",
                        List.of("First version", "Second version")
                ),
                new PublishInfo(
                        "Издание первое",
                        "Издательство",
                        "Москва",
                        "2024",
                        "978-3-16-148410-0"
                )
        );

        // Добавляем ресурс обложки для coverpage
        Resource coverResource = new Resource("cover", "image/jpeg",
                () -> new ByteArrayInputStream(new byte[0]));

        FictionBookDto book = new FictionBookDto(desc, List.of(), Map.of("cover", coverResource));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Fb2Writer writer = new Fb2Writer();
        writer.write(book, baos);

        String xml = baos.toString("UTF-8");

        // Title-info
        assertThat(xml).contains("<book-title>Тестовая книга</book-title>");
        assertThat(xml).contains("<first-name>Иван</first-name>");
        assertThat(xml).contains("<middle-name>Иванович</middle-name>");
        assertThat(xml).contains("<last-name>Иванов</last-name>");
        assertThat(xml).contains("<genre>fiction</genre>");
        assertThat(xml).contains("<genre>fantasy</genre>");
        assertThat(xml).contains("<lang>ru</lang>");
        assertThat(xml).contains("<src-lang>en</src-lang>");
        assertThat(xml).contains("<annotation>");
        assertThat(xml).contains("<p>Аннотация книги</p>");
        assertThat(xml).contains("<sequence name=\"Серия книг\" number=\"1\"");
        assertThat(xml).contains("<coverpage>");
        assertThat(xml).contains("l:href=\"#cover\"");

        // Document-info
        assertThat(xml).contains("<program-used>Custom OCR Program</program-used>");
        assertThat(xml).contains("<date>2024-01-15</date>");
        assertThat(xml).contains("<id>unique-id-123</id>");
        assertThat(xml).contains("<version>2.5</version>");
        assertThat(xml).contains("<history>");
        assertThat(xml).contains("<p>First version</p>");
        assertThat(xml).contains("<p>Second version</p>");

        // Publish-info
        assertThat(xml).contains("<publisher>Издательство</publisher>");
        assertThat(xml).contains("<city>Москва</city>");
        assertThat(xml).contains("<year>2024</year>");
        assertThat(xml).contains("<isbn>978-3-16-148410-0</isbn>");
    }

    @Test
    void shouldWriteParagraphWithFormatting() throws Exception {
        Paragraph para = new Paragraph(List.of(
                new Text("Обычный "),
                new Strong(List.of(new Text("жирный"))),
                new Text(" и "),
                new Emphasis(List.of(new Text("курсив"))),
                new Text(" с "),
                new Strikethrough(List.of(new Text("зачёркнутым"))),
                new Text(" текстом")
        ));

        FictionBookDto book = createBookWithParagraph(para);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Fb2Writer().write(book, baos);

        String xml = baos.toString("UTF-8");

        assertThat(xml).contains("<p>Обычный <strong>жирный</strong> и <emphasis>курсив</emphasis> с <strikethrough>зачёркнутым</strikethrough> текстом</p>");
    }

    @Test
    void shouldWriteNestedFormatting() throws Exception {
        // Жирный + курсив одновременно
        Paragraph para = new Paragraph(List.of(
                new Text("Обычный "),
                new Strong(List.of(
                        new Text("жирный "),
                        new Emphasis(List.of(new Text("и курсив")))
                )),
                new Text(" текст")
        ));

        FictionBookDto book = createBookWithParagraph(para);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Fb2Writer().write(book, baos);

        String xml = baos.toString("UTF-8");

        assertThat(xml).contains("<strong>жирный <emphasis>и курсив</emphasis></strong>");
    }

    @Test
    void shouldWriteLinkWithXlinkHref() throws Exception {
        Link link = new Link("#note1", "note", List.of(new Text("[1]")));
        Paragraph para = new Paragraph(List.of(new Text("Текст со "), link, new Text(" сноской")));

        FictionBookDto book = createBookWithParagraph(para);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Fb2Writer().write(book, baos);

        String xml = baos.toString("UTF-8");

        assertThat(xml).contains("<a l:href=\"#note1\" type=\"note\">[1]</a>");
    }

    @Test
    void shouldWriteExternalLink() throws Exception {
        Link link = new Link("https://example.com", null, List.of(new Text("Внешняя ссылка")));
        Paragraph para = new Paragraph(List.of(link));

        FictionBookDto book = createBookWithParagraph(para);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Fb2Writer().write(book, baos);

        String xml = baos.toString("UTF-8");

        assertThat(xml).contains("<a l:href=\"https://example.com\">Внешняя ссылка</a>");
    }

    @Test
    void shouldWriteImageRef() throws Exception {
        ImageRef img = new ImageRef("#cover", "Обложка книги");
        Paragraph para = new Paragraph(List.of(img));
        Section section = new Section(null, List.of(), List.of(para), List.of(), Map.of());

        // ✅ Добавляем ресурс, чтобы санитайзер не удалил ссылку
        Resource coverResource = new Resource("cover", "image/jpeg",
                () -> new ByteArrayInputStream(new byte[0]));

        FictionBookDto book = new FictionBookDto(
                createMinimalDescription(),
                List.of(new BodyDto(null, List.of(section))),
                Map.of("cover", coverResource));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Fb2Writer().write(book, baos);

        String xml = baos.toString("UTF-8");

        assertThat(xml).contains("l:href=\"#cover\"");
        assertThat(xml).contains("alt=\"Обложка книги\"");
    }

    @Test
    void shouldWriteBinaryWithBase64() throws Exception {
        byte[] imageData = "fake image data for testing".getBytes();
        Resource resource = new Resource("cover", "image/jpeg",
                () -> new ByteArrayInputStream(imageData));

        FictionBookDto book = new FictionBookDto(
                createMinimalDescription(),
                List.of(),
                Map.of("cover", resource));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Fb2Writer().write(book, baos);

        String xml = baos.toString("UTF-8");

        String expectedBase64 = Base64.getEncoder().encodeToString(imageData);

        assertThat(xml).contains("<binary id=\"cover\" content-type=\"image/jpeg\">");
        assertThat(xml).contains(expectedBase64);
        assertThat(xml).contains("</binary>");
    }

    @Test
    void shouldApplySanitizersByDefault() throws Exception {
        // Создаём книгу с пустыми параграфами и разбитыми Text-нодами
        Section section = new Section("ch1", List.of(),
                List.of(
                        new Paragraph(List.of()),  // Пустой — должен удалиться
                        new Paragraph(List.of(new Text("   "))),  // Пробельный — должен удалиться
                        new Paragraph(List.of(new Text("Разбитый "), new Text("текст"))),  // Склеится
                        new Paragraph(List.of(new Text("Нормальный текст")))
                ),
                List.of(), Map.of());

        FictionBookDto book = createBookWithSection(section);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Fb2Writer().write(book, baos);

        String xml = baos.toString("UTF-8");

        // Пустые параграфы должны быть удалены
        assertThat(xml).doesNotContain("<p/>");
        assertThat(xml).doesNotContain("<p></p>");
        assertThat(xml).doesNotContain("<p>   </p>");

        // Разбитый текст должен быть склеен
        assertThat(xml).contains("<p>Разбитый текст</p>");

        // Нормальный параграф должен остаться
        assertThat(xml).contains("<p>Нормальный текст</p>");
    }

    @Test
    void shouldDisableSanitizersWhenRequested() throws Exception {
        Section section = new Section(null, List.of(),
                List.of(
                        new Paragraph(List.of()),  // Пустой параграф — должен остаться
                        new Paragraph(List.of(new Text("Текст")))
                ),
                List.of(), Map.of());

        FictionBookDto book = createBookWithSection(section);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Fb2Writer writer = new Fb2Writer();
        writer.setSanitizerPipeline(null); // Отключаем санитайзеры
        writer.write(book, baos);

        String xml = baos.toString("UTF-8");

        // ✅ Пустой параграф должен остаться (StAX генерирует <p/> для пустых элементов)
        assertThat(xml).contains("<p/>");
        assertThat(xml).contains("<p>Текст</p>");
    }

    @Test
    void shouldUseCustomSanitizerPipeline() throws Exception {
        Section section = new Section(null, List.of(),
                List.of(
                        new Paragraph(List.of()),  // Пустой
                        new Paragraph(List.of(new Text("Разбитый "), new Text("текст")))
                ),
                List.of(), Map.of());

        FictionBookDto book = createBookWithSection(section);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Fb2Writer writer = new Fb2Writer();
        // Только склейка текстов, без удаления пустых параграфов
        writer.setSanitizerPipeline(SanitizerPipeline.builder()
                .add(new TextNodeMerger())
                .build());
        writer.write(book, baos);

        String xml = baos.toString("UTF-8");

        // Пустой параграф должен остаться
        assertThat(xml).contains("<p/>");
        // Текст должен быть склеен
        assertThat(xml).contains("<p>Разбитый текст</p>");
    }

    @Test
    void roundTripShouldPreserveData() throws Exception {
        // Создаём книгу с богатым содержанием
        Paragraph para1 = new Paragraph(List.of(
                new Text("Первый параграф с "),
                new Strong(List.of(new Text("жирным"))),
                new Text(" текстом")
        ));
        Paragraph para2 = new Paragraph(List.of(new Text("Второй параграф")));

        Section section = new Section(
                "chapter1",
                List.of(new Paragraph(List.of(new Text("Глава 1")))),
                List.of(para1, para2),
                List.of(),
                Map.of()
        );

        byte[] coverData = "fake cover".getBytes();
        Resource cover = new Resource("cover", "image/jpeg",
                () -> new ByteArrayInputStream(coverData));

        FictionBookDto original = new FictionBookDto(
                createMinimalDescription(),
                List.of(new BodyDto(null, List.of(section))),
                Map.of("cover", cover)
        );

        Path tempFile = tempDir.resolve("roundtrip.fb2");

        // Записываем
        FictionBookIO.write(original, tempFile);

        // Читаем обратно
        FictionBookDto reread = FictionBookIO.read(tempFile);

        // Сравниваем ключевые поля
        assertThat(reread.description().titleInfo().bookTitle())
                .isEqualTo(original.description().titleInfo().bookTitle());
        assertThat(reread.bodies()).hasSize(1);
        assertThat(reread.bodies().get(0).sections()).hasSize(1);

        Section rereadSection = reread.bodies().get(0).sections().get(0);
        assertThat(rereadSection.id()).isEqualTo("chapter1");
        assertThat(rereadSection.content()).hasSize(2);

        // Проверяем бинарник
        assertThat(reread.resources()).containsKey("cover");
        byte[] rereadCoverData;
        try (var is = reread.resources().get("cover").dataProvider().getInputStream()) {
            rereadCoverData = is.readAllBytes();
        }
        assertThat(rereadCoverData).isEqualTo(coverData);
    }

    @Test
    void shouldWriteMultipleBodies() throws Exception {
        // Основное тело
        Section mainSection = new Section("ch1",
                List.of(new Paragraph(List.of(new Text("Основная глава")))),
                List.of(new Paragraph(List.of(new Text("Текст")))),
                List.of(), Map.of());
        BodyDto mainBody = new BodyDto(null, List.of(mainSection));

        // Тело примечаний
        Section noteSection = new Section("n1",
                List.of(),
                List.of(new Paragraph(List.of(new Text("Примечание 1")))),
                List.of(), Map.of());
        BodyDto notesBody = new BodyDto("notes", List.of(noteSection));

        FictionBookDto book = new FictionBookDto(
                createMinimalDescription(),
                List.of(mainBody, notesBody),
                Map.of()
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Fb2Writer().write(book, baos);

        String xml = baos.toString("UTF-8");

        assertThat(xml).contains("<body>");
        assertThat(xml).contains("<body name=\"notes\">");
        assertThat(xml).contains("<section id=\"ch1\">");
        assertThat(xml).contains("<section id=\"n1\">");
    }

    @Test
    void shouldWriteEmptyLine() throws Exception {
        Section section = new Section(null, List.of(),
                List.of(
                        new Paragraph(List.of(new Text("До пустой строки"))),
                        new EmptyLine(),
                        new Paragraph(List.of(new Text("После пустой строки")))
                ),
                List.of(), Map.of());

        FictionBookDto book = createBookWithSection(section);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Fb2Writer().write(book, baos);

        String xml = baos.toString("UTF-8");

        assertThat(xml).contains("<p>До пустой строки</p>");
        assertThat(xml).contains("<empty-line/>");
        assertThat(xml).contains("<p>После пустой строки</p>");
    }

    @Test
    void shouldWriteSubAndSup() throws Exception {
        Paragraph para = new Paragraph(List.of(
                new Text("H"),
                new Sub(List.of(new Text("2"))),
                new Text("O и x"),
                new Sup(List.of(new Text("2")))
        ));

        FictionBookDto book = createBookWithParagraph(para);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Fb2Writer().write(book, baos);

        String xml = baos.toString("UTF-8");

        assertThat(xml).contains("H<sub>2</sub>O");
        assertThat(xml).contains("x<sup>2</sup>");
    }

    @Test
    void shouldEscapeSpecialCharacters() throws Exception {
        Paragraph para = new Paragraph(List.of(
                new Text("Спецсимволы: < > & \" ' в тексте")
        ));

        FictionBookDto book = createBookWithParagraph(para);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Fb2Writer().write(book, baos);

        String xml = baos.toString("UTF-8");

        // XMLStreamWriter ОБЯЗАН экранировать < и & в тексте
        assertThat(xml).contains("&lt;");
        assertThat(xml).contains("&amp;");

        // Символы >, ", ' валидны в XML text nodes без экранирования
        assertThat(xml).contains(">");
        assertThat(xml).contains("\"");
        assertThat(xml).contains("'");

        // Убеждаемся, что некорректного XML не получилось
        assertThat(xml).doesNotContain("Спецсимволы: < >"); // < должен быть &lt;
    }

    @Test
    void shouldWriteNestedSections() throws Exception {
        Section childSection = new Section("child",
                List.of(),
                List.of(new Paragraph(List.of(new Text("Дочерняя секция")))),
                List.of(), Map.of());

        Section parentSection = new Section("parent",
                List.of(new Paragraph(List.of(new Text("Родительская секция")))),
                List.of(new Paragraph(List.of(new Text("Текст родителя")))),
                List.of(childSection), Map.of());

        FictionBookDto book = createBookWithSection(parentSection);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Fb2Writer().write(book, baos);

        String xml = baos.toString("UTF-8");

        int parentStart = xml.indexOf("<section id=\"parent\">");
        int childStart = xml.indexOf("<section id=\"child\">");

        // Конец дочерней секции
        int childEnd = xml.indexOf("</section>", childStart);

        // ✅ Ищем закрывающий тег родителя ПОСЛЕ закрывающего тега ребёнка
        int parentEnd = xml.indexOf("</section>", childEnd + 1);

        assertThat(parentStart).isGreaterThan(-1);
        assertThat(childStart).isGreaterThan(parentStart);
        assertThat(childEnd).isGreaterThan(childStart);
        assertThat(parentEnd).isGreaterThan(childEnd); // Теперь это true
    }

    @Test
    void shouldWritePrettyPrintedOutput() throws Exception {
        FictionBookDto book = createMinimalBook();
        Path output = tempDir.resolve("pretty.fb2");

        Fb2Writer writer = new Fb2Writer();
        writer.setPrettyPrint(true);
        writer.write(book, output);

        String xml = Files.readString(output);

        // Должны быть переносы строк
        assertThat(xml).contains("\n");
        assertThat(xml.split("\n").length).isGreaterThan(5);
    }

    @Test
    void shouldWriteCompactOutput() throws Exception {
        FictionBookDto book = createMinimalBook();
        Path output = tempDir.resolve("compact.fb2");

        Fb2Writer writer = new Fb2Writer();
        writer.setPrettyPrint(false);
        writer.write(book, output);

        String xml = Files.readString(output);

        // Должно быть минимальное количество переносов строк
        long newlineCount = xml.chars().filter(c -> c == '\n').count();
        assertThat(newlineCount).isLessThan(10);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Создаёт минимальный валидный Description для тестов.
     */
    private Description createMinimalDescription() {
        return new Description(
                new TitleInfo(
                        List.of(),
                        List.of("fiction"),
                        "Test Book",
                        List.of(),
                        "en",
                        null,
                        null,
                        List.of()
                ),
                new DocumentInfo(
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        "test-id",
                        "1.0",
                        List.of()
                ),
                null
        );
    }

    /**
     * Создаёт минимальную книгу с одной секцией и одним параграфом.
     */
    private FictionBookDto createMinimalBook() {
        Paragraph para = new Paragraph(List.of(new Text("Hello, world!")));
        Section section = new Section("ch1", List.of(), List.of(para), List.of(), Map.of());
        BodyDto body = new BodyDto(null, List.of(section));

        return new FictionBookDto(
                createMinimalDescription(),
                List.of(body),
                Map.of()
        );
    }

    /**
     * Создаёт книгу с заданной секцией.
     */
    private FictionBookDto createBookWithSection(Section section) {
        BodyDto body = new BodyDto(null, List.of(section));
        return new FictionBookDto(
                createMinimalDescription(),
                List.of(body),
                Map.of()
        );
    }

    /**
     * Создаёт книгу с заданным параграфом в единственной секции.
     */
    private FictionBookDto createBookWithParagraph(Paragraph para) {
        Section section = new Section(null, List.of(), List.of(para), List.of(), Map.of());
        return createBookWithSection(section);
    }

    /**
     * Создаёт книгу с текстом в единственном параграфе.
     */
    private FictionBookDto createBookWithParagraph(String text) {
        return createBookWithParagraph(new Paragraph(List.of(new Text(text))));
    }
}