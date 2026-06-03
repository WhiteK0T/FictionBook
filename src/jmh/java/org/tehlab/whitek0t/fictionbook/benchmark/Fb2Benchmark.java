package org.tehlab.whitek0t.fictionbook.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.description.Author;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.dto.description.DocumentInfo;
import org.tehlab.whitek0t.fictionbook.dto.description.TitleInfo;
import org.tehlab.whitek0t.fictionbook.dto.inline.Emphasis;
import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Link;
import org.tehlab.whitek0t.fictionbook.dto.inline.Strong;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;
import org.tehlab.whitek0t.fictionbook.internal.reader.fb2.Fb2Reader;
import org.tehlab.whitek0t.fictionbook.internal.sanitizer.SanitizerPipeline;
import org.tehlab.whitek0t.fictionbook.internal.writer.fb2.Fb2Writer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH-бенчмарки горячего пути библиотеки: парсинг ({@link Fb2Reader}), сериализация
 * ({@link Fb2Writer}, включающая прогон санитайзеров) и сам конвейер санитайзеров
 * ({@link SanitizerPipeline}).
 *
 * <p>Книга строится синтетически в {@link #setup()} и масштабируется параметром
 * {@code sections}, чтобы видеть, как растёт стоимость с размером документа. Размеры
 * подобраны репрезентативно: 10 секций — рассказ, 100 — средний роман.</p>
 *
 * <p>Запуск: {@code ./gradlew jmh}. Для точных цифр поднять число итераций/форков
 * в блоке {@code jmh { … }} в build.gradle.kts.</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class Fb2Benchmark {

    /** Размер книги: число секций верхнего уровня. */
    @Param({"10", "100"})
    public int sections;

    private final Fb2Reader reader = new Fb2Reader();
    private final Fb2Writer writer = new Fb2Writer();
    private final SanitizerPipeline pipeline = SanitizerPipeline.standard();

    /** Готовая DTO-книга — вход для write/sanitize. */
    private FictionBookDto book;
    /** Сериализованная книга — вход для read. */
    private byte[] fb2Bytes;

    @Setup
    public void setup() throws Exception {
        book = buildBook(sections);
        fb2Bytes = toBytes(book);
    }

    // ========================================================================
    // БЕНЧМАРКИ
    // ========================================================================

    /** Парсинг байтов FB2 в DTO (encoding-detection минует — поток уже UTF-8). */
    @Benchmark
    public FictionBookDto read() throws Exception {
        return reader.read(new ByteArrayInputStream(fb2Bytes));
    }

    /** Сериализация DTO в байты FB2 (включает авто-прогон санитайзеров перед записью). */
    @Benchmark
    public byte[] write() throws Exception {
        return toBytes(book);
    }

    /** Изолированный прогон стандартного конвейера санитайзеров. */
    @Benchmark
    public FictionBookDto sanitize() {
        return pipeline.sanitize(book);
    }

    /** Полный цикл read→write. */
    @Benchmark
    public byte[] roundTrip() throws Exception {
        FictionBookDto parsed = reader.read(new ByteArrayInputStream(fb2Bytes));
        return toBytes(parsed);
    }

    // ========================================================================
    // I/O ХЕЛПЕР
    // ========================================================================

    private byte[] toBytes(FictionBookDto dto) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);
        writer.write(dto, baos);
        return baos.toByteArray();
    }

    // ========================================================================
    // СБОРКА СИНТЕТИЧЕСКОЙ КНИГИ
    // ========================================================================

    private static FictionBookDto buildBook(int sectionCount) {
        List<Section> tops = new ArrayList<>(sectionCount);
        for (int i = 0; i < sectionCount; i++) {
            tops.add(buildSection(i));
        }
        BodyDto body = new BodyDto(null, tops);

        Map<String, Resource> resources = new LinkedHashMap<>();
        byte[] blob = new byte[2048];
        for (int i = 0; i < blob.length; i++) {
            blob[i] = (byte) (i % 256);
        }
        resources.put("cover", new Resource("cover", "image/jpeg",
                () -> new ByteArrayInputStream(blob)));

        return new FictionBookDto(description(), List.of(body), resources);
    }

    /** Секция с заголовком и пятью параграфами смешанного inline-форматирования. */
    private static Section buildSection(int index) {
        List<BlockElement> title = List.of(
                new Paragraph(List.of(new Text("Глава " + index))));

        List<BlockElement> content = new ArrayList<>(5);
        for (int p = 0; p < 5; p++) {
            content.add(new Paragraph(List.of(
                    new Text("Обычный текст параграфа " + p + ", "),
                    new Strong(List.of(new Text("жирный фрагмент"))),
                    new Text(" затем "),
                    new Emphasis(List.of(new Text("курсив"))),
                    new Text(" и ссылка "),
                    new Link("#ch" + index, "note", List.of(new Text("[" + index + "]"))),
                    new Text(" в конце.")
            )));
        }

        return new Section("ch" + index, title, content, List.of(), Map.of());
    }

    private static Description description() {
        TitleInfo titleInfo = new TitleInfo(
                List.of(new Author("Имя", null, "Фамилия")),
                List.of("prose_classic"),
                "Бенчмарк-книга",
                List.of(),
                "ru", null, null, List.of());
        DocumentInfo documentInfo = new DocumentInfo(
                List.of(), null, null, null, null, "bench-1", "1.0", List.of());
        return new Description(titleInfo, documentInfo, null);
    }

    /** Не используется JMH напрямую, но удобно для ручной проверки кодировки сэмпла. */
    static String sampleXml(int sectionCount) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Fb2Writer().write(buildBook(sectionCount), baos);
        return baos.toString(StandardCharsets.UTF_8);
    }
}
