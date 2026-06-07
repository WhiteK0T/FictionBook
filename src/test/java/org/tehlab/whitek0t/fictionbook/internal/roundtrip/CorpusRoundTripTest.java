package org.tehlab.whitek0t.fictionbook.internal.roundtrip;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.tehlab.whitek0t.fictionbook.api.FictionBookFormat;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.internal.reader.fb2.Fb2Reader;
import org.tehlab.whitek0t.fictionbook.internal.reader.fb3.Fb3Reader;
import org.tehlab.whitek0t.fictionbook.internal.writer.fb2.Fb2Writer;
import org.tehlab.whitek0t.fictionbook.internal.writer.fb3.Fb3Writer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Корпусной round-trip на <b>реальных</b> файлах из {@code src/test/resources/books}.
 * <p>
 * В отличие от {@link Fb2RoundTripTest} (книги собираются в коде), этот тест берёт
 * настоящие FB2/FB3-файлы, которые часто бывают «грязными», и проверяет на них
 * главный инвариант стратегии «прощающее чтение / строгая запись»: после первой
 * строгой записи книга выходит на <b>фикспоинт</b>.
 * <pre>
 *   dto1   = read(файл)     // прощающее чтение «как есть»
 *   bytes1 = write(dto1)    // первая строгая запись (с санитайзингом)
 *   dto2   = read(bytes1)
 *   bytes2 = write(dto2)
 *   assert bytes1 == bytes2 // байт-в-байт фикспоинт
 * </pre>
 * Прямое сравнение DTO здесь неприменимо: ресурсы хранят
 * {@code ResourceDataProvider} (лямбды) без осмысленного {@code equals}. Поэтому
 * сравниваются именно байты второго и третьего проходов — это устойчиво к любому
 * исходному «мусору» и точно ловит дрейф reader/writer.
 * <p>
 * Файлы кладутся в {@code src/test/resources/books} (см. README там же). Если папка
 * пуста или отсутствует — тест не падает, а подсказывает, куда класть файлы.
 */
@DisplayName("Корпусной round-trip (реальные файлы из resources/books)")
class CorpusRoundTripTest {

    /** Корень корпуса относительно рабочей директории Gradle (корень проекта). */
    private static final Path CORPUS = Path.of("src", "test", "resources", "books");

    /** Фиксированная метка времени ZIP-записей FB3 — для детерминированного фикспоинта. */
    private static final LocalDateTime FIXED_ENTRY_TIME = LocalDateTime.of(1980, 1, 1, 0, 0, 0);

    @TestFactory
    @DisplayName("фикспоинт write→read→write для каждого файла корпуса")
    Stream<DynamicTest> fixpointForEachBook() throws IOException {
        if (!Files.isDirectory(CORPUS)) {
            return Stream.of(DynamicTest.dynamicTest(
                    "корпус отсутствует — положите *.fb2/*.fb3 в " + CORPUS,
                    () -> { /* нет файлов — нечего проверять */ }));
        }

        List<Path> books;
        try (Stream<Path> walk = Files.walk(CORPUS)) {
            books = walk.filter(Files::isRegularFile)
                    .filter(CorpusRoundTripTest::isBook)
                    .sorted()
                    .toList();
        }

        if (books.isEmpty()) {
            return Stream.of(DynamicTest.dynamicTest(
                    "корпус пуст — положите *.fb2/*.fb3 в " + CORPUS,
                    () -> { /* нет файлов — нечего проверять */ }));
        }

        return books.stream().map(book -> DynamicTest.dynamicTest(
                CORPUS.relativize(book).toString(),
                () -> assertFixpoint(book)));
    }

    /** Прогоняет один файл по циклу и проверяет байт-в-байт фикспоинт. */
    private static void assertFixpoint(Path book) throws Exception {
        FictionBookFormat format = FictionBookFormat.detect(book);

        FictionBookDto dto1 = read(Files.readAllBytes(book), format);
        assertThat(dto1).as("чтение исходного файла %s", book).isNotNull();
        assertThat(dto1.bodies()).as("у книги %s должно быть тело", book).isNotEmpty();

        byte[] bytes1 = write(dto1, format);
        FictionBookDto dto2 = read(bytes1, format);
        byte[] bytes2 = write(dto2, format);

        assertThat(bytes2)
                .as("байт-в-байт фикспоинт после первой строгой записи: %s", book)
                .isEqualTo(bytes1);
    }

    private static FictionBookDto read(byte[] data, FictionBookFormat format) throws Exception {
        return switch (format) {
            case FB2 -> new Fb2Reader().read(new ByteArrayInputStream(data));
            case FB3 -> new Fb3Reader().read(new ByteArrayInputStream(data));
        };
    }

    private static byte[] write(FictionBookDto book, FictionBookFormat format) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        switch (format) {
            case FB2 -> new Fb2Writer().write(book, baos);
            case FB3 -> {
                Fb3Writer writer = new Fb3Writer();
                // Фиксируем время ZIP-записей, иначе байт-в-байт фикспоинт нестабилен
                // (ZipEntry по умолчанию берёт текущее время).
                writer.setEntryTime(FIXED_ENTRY_TIME);
                writer.write(book, baos);
            }
        }
        return baos.toByteArray();
    }

    private static boolean isBook(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        return name.endsWith(".fb2") || name.endsWith(".fb3");
    }
}
