package org.tehlab.whitek0t.fictionbook.internal.reader.fb2;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-фьюз «прощающего чтения»: на любом входе — случайных байтах или покалеченном
 * FB2 — {@link Fb2Reader} обязан соблюдать единый контракт устойчивости: <b>либо вернуть
 * непустой {@link FictionBookDto}, либо бросить {@link FictionBookException}</b> (его
 * подкласс {@code InvalidFormatException} допустим). Любое иное исключение или ошибка
 * ({@code NullPointerException}, голый {@code XMLStreamException}, {@code StackOverflowError},
 * {@code IllegalArgumentException} из base64 и т.п.), просочившееся наружу, — это баг
 * устойчивости.
 *
 * <p><b>Важно:</b> класс намеренно <i>чисто jqwik-овский</i> — без JUnit-Jupiter аннотаций
 * ({@code @Test}/{@code @Nested}/{@code @DisplayName}). Если смешать {@code @Property} с
 * Jupiter-конструкциями в одном классе, класс «забирает» движок Jupiter, а движок jqwik
 * перестаёт исполнять свои {@code @Property} (они докладываются как skipped и не гоняются).
 * Детерминированные краевые случаи на {@code @Test} вынесены в {@link Fb2ReaderFuzzTest}.</p>
 *
 * <p>Фьюзим через {@link Fb2Reader#read(java.io.InputStream)} — этот путь отдаёт байты
 * прямо в StAX (UTF-8), без эвристик кодировки, поэтому контракт проверяется детерминированно.</p>
 */
class Fb2ReaderFuzzPropertyTest {

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
    // PROPERTY-ФЬЮЗ
    // ========================================================================

    @Property(tries = 500)
    @Label("any random byte string is read forgivingly or rejected cleanly")
    void randomBytesNeverThrowUncontrolled(@ForAll("randomBytes") byte[] data) {
        assertForgiving(data);
    }

    @Property(tries = 500)
    @Label("any corruption of a valid FB2 is read forgivingly or rejected cleanly")
    void corruptedValidFb2NeverThrowUncontrolled(@ForAll("corruptedFb2") byte[] data) {
        assertForgiving(data);
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

    /** Короткое читаемое описание входа для сообщения об ошибке (с обрезкой). */
    private static String describe(byte[] data) {
        String s = new String(data, StandardCharsets.UTF_8);
        if (s.length() > 120) s = s.substring(0, 120) + "…(" + data.length + " bytes)";
        return "[" + s.replace('\n', ' ') + "]";
    }

    // ========================================================================
    // ГЕНЕРАТОРЫ
    // ========================================================================

    @Provide
    Arbitrary<byte[]> randomBytes() {
        return Arbitraries.bytes().array(byte[].class).ofMinSize(0).ofMaxSize(512);
    }

    /**
     * Берёт эталонный FB2 и применяет одну из мутаций: обрезку, замену байта,
     * вырезание куска или вставку мусора. Так покрываем «почти валидный» вход,
     * до которого случайные байты не доберутся.
     */
    @Provide
    Arbitrary<byte[]> corruptedFb2() {
        Arbitrary<Integer> op = Arbitraries.integers().between(0, 3);
        Arbitrary<Integer> idx = Arbitraries.integers().between(0, VALID_FB2.length);
        Arbitrary<Integer> len = Arbitraries.integers().between(1, 48);
        Arbitrary<Byte> fill = Arbitraries.bytes();
        return Combinators.combine(op, idx, len, fill).as(Fb2ReaderFuzzPropertyTest::mutate);
    }

    private static byte[] mutate(int op, int idx, int len, byte fill) {
        byte[] src = VALID_FB2;
        switch (op) {
            case 0: // обрезка до случайной длины
                return Arrays.copyOf(src, Math.min(idx, src.length));
            case 1: { // замена одного байта
                byte[] c = src.clone();
                c[idx % c.length] = fill;
                return c;
            }
            case 2: { // вырезание куска
                int start = Math.min(idx, src.length);
                int end = Math.min(start + len, src.length);
                byte[] c = new byte[src.length - (end - start)];
                System.arraycopy(src, 0, c, 0, start);
                System.arraycopy(src, end, c, start, src.length - end);
                return c;
            }
            default: { // вставка прогона мусорных байтов
                int pos = Math.min(idx, src.length);
                byte[] c = new byte[src.length + len];
                System.arraycopy(src, 0, c, 0, pos);
                Arrays.fill(c, pos, pos + len, fill);
                System.arraycopy(src, pos, c, pos + len, src.length - pos);
                return c;
            }
        }
    }
}
