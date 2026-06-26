package org.tehlab.whitek0t.fictionbook.internal.reader.fb2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Детерминированные fuzz-случаи «прощающего чтения» на JUnit Jupiter ({@code @Test}):
 * именованные «грязные» входы как документация поведения. {@link Fb2Reader} обязан
 * соблюдать единый контракт устойчивости: <b>либо вернуть непустой {@link FictionBookDto},
 * либо бросить {@link FictionBookException}</b> (его подкласс {@code InvalidFormatException}
 * допустим). Любое иное исключение или ошибка ({@code NullPointerException}, голый
 * {@code XMLStreamException}, {@code StackOverflowError}, {@code IllegalArgumentException}
 * из base64 и т.п.), просочившееся наружу, — это баг устойчивости.
 *
 * <p>Рандомизированный property-фьюз ({@code @Property}) вынесен в отдельный чисто
 * jqwik-овский класс {@link Fb2ReaderFuzzPropertyTest}: в одном классе мешать
 * {@code @Property} и Jupiter-конструкции нельзя — иначе jqwik перестаёт исполнять
 * свои проперти (они докладываются как skipped).</p>
 */
class Fb2ReaderFuzzTest {

    private final Fb2Reader reader = new Fb2Reader();

    /** Эталонный валидный FB2 — основа для мутаций. */
    private static final byte[] VALID_FB2 = ("""
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"
                         xmlns:l="http://www.w3.org/1999/xlink">
                <description>
                    <title-info>
                        <genre>prose_classic</genre>
                        <author><first-name>Имя</first-name><last-name>Фамилия</last-name></author>
                        <book-title>Книга</book-title>
                        <lang>ru</lang>
                    </title-info>
                    <document-info><id>doc-1</id><version>1.0</version></document-info>
                </description>
                <body>
                    <section id="ch1">
                        <title><p>Глава</p></title>
                        <p>Обычный <strong>жирный</strong> текст и <a l:href="#ch1">ссылка</a>.</p>
                        <p>Второй параграф.</p>
                    </section>
                </body>
                <binary id="img" content-type="image/png">iVBORw0KGgo=</binary>
            </FictionBook>
            """).getBytes(StandardCharsets.UTF_8);

    // ========================================================================
    // ДЕТЕРМИНИРОВАННЫЕ КРАЕВЫЕ СЛУЧАИ
    // ========================================================================

    @Nested
    @DisplayName("Specific garbage inputs")
    class SpecificGarbageInputs {

        @Test
        @DisplayName("empty input is rejected as FictionBookException")
        void emptyInput() {
            assertRejected(new byte[0]);
        }

        @Test
        @DisplayName("NUL bytes are rejected as FictionBookException")
        void nulBytes() {
            assertRejected(new byte[]{0, 0, 0, 0, 0, 0, 0, 0});
        }

        @Test
        @DisplayName("plain non-XML text is rejected as FictionBookException")
        void plainText() {
            assertRejected("это вообще не xml, просто текст".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("well-formed XML that is not a FictionBook is rejected (missing <description>)")
        void foreignXml() {
            assertRejected("<html><body><p>hi</p></body></html>".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("FB2 truncated mid-tag is rejected as FictionBookException")
        void truncatedMidTag() {
            byte[] half = Arrays.copyOf(VALID_FB2, VALID_FB2.length / 2);
            assertRejected(half);
        }

        @Test
        @DisplayName("invalid base64 in <binary> is swallowed, book still reads")
        void invalidBase64InBinary() {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook>
                        <description>
                            <title-info><book-title>t</book-title><lang>ru</lang></title-info>
                            <document-info><id>i</id><version>1.0</version></document-info>
                        </description>
                        <body><section><p>x</p></section></body>
                        <binary id="b" content-type="image/png">!!! не base64 !!!</binary>
                    </FictionBook>
                    """;
            FictionBookDto book = readOrFail(xml.getBytes(StandardCharsets.UTF_8));
            // Битый бинарник не валит чтение: ресурс присутствует с пустыми данными.
            assertThat(book.resources()).containsKey("b");
        }

        @Test
        @DisplayName("moderately deep section nesting still reads to a DTO")
        void deepButBoundedNesting() {
            int depth = 200;
            StringBuilder sb = new StringBuilder("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook>
                        <description>
                            <title-info><book-title>t</book-title><lang>ru</lang></title-info>
                            <document-info><id>i</id><version>1.0</version></document-info>
                        </description>
                        <body>""");
            sb.repeat("<section>", depth);
            sb.append("<p>дно</p>");
            sb.repeat("</section>", depth);
            sb.append("</body></FictionBook>");

            FictionBookDto book = readOrFail(sb.toString().getBytes(StandardCharsets.UTF_8));
            assertThat(book.bodies()).hasSize(1);
        }

        @Test
        @DisplayName("pathologically deep nesting is rejected cleanly, never StackOverflowError")
        void pathologicallyDeepNesting() {
            int depth = 50_000;
            StringBuilder sb = new StringBuilder("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook>
                        <description>
                            <title-info><book-title>t</book-title><lang>ru</lang></title-info>
                            <document-info><id>i</id><version>1.0</version></document-info>
                        </description>
                        <body>""");
            sb.repeat("<section>", depth);
            sb.append("<p>дно</p>");
            sb.repeat("</section>", depth);
            sb.append("</body></FictionBook>");

            // Не важно, прочитается или отвергнется — важно, что это не StackOverflowError
            // и не любая другая неконтролируемая ошибка.
            assertForgiving(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    // ========================================================================
    // КОНТРАКТ
    // ========================================================================

    /** Главный инвариант: либо непустой DTO, либо {@link FictionBookException} — и ничего иного. */
    private void assertForgiving(byte[] data) {
        try {
            FictionBookDto dto = reader.read(new ByteArrayInputStream(data));
            assertThat(dto)
                    .as("успешное чтение обязано вернуть непустой DTO")
                    .isNotNull();
        } catch (FictionBookException expected) {
            // Допустимый исход прощающего чтения.
        } catch (Throwable unexpected) {
            throw new AssertionError(
                    "read(...) бросил " + unexpected.getClass().getName()
                            + " вместо FictionBookException на входе " + describe(data),
                    unexpected);
        }
    }

    private void assertRejected(byte[] data) {
        try {
            reader.read(new ByteArrayInputStream(data));
            throw new AssertionError("ожидался FictionBookException на входе " + describe(data));
        } catch (FictionBookException expected) {
            // ok
        } catch (Throwable unexpected) {
            throw new AssertionError(
                    "read(...) бросил " + unexpected.getClass().getName()
                            + " вместо FictionBookException на входе " + describe(data),
                    unexpected);
        }
    }

    private FictionBookDto readOrFail(byte[] data) {
        try {
            return reader.read(new ByteArrayInputStream(data));
        } catch (FictionBookException e) {
            throw new AssertionError("ожидалось успешное чтение, а получили " + e, e);
        }
    }

    /** Короткое читаемое описание входа для сообщения об ошибке (с обрезкой). */
    private static String describe(byte[] data) {
        String s = new String(data, StandardCharsets.UTF_8);
        if (s.length() > 120) s = s.substring(0, 120) + "…(" + data.length + " bytes)";
        return "[" + s.replace('\n', ' ') + "]";
    }
}
