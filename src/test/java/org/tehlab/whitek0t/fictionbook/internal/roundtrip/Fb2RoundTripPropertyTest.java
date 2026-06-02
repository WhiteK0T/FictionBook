package org.tehlab.whitek0t.fictionbook.internal.roundtrip;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
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
import org.tehlab.whitek0t.fictionbook.dto.inline.Sub;
import org.tehlab.whitek0t.fictionbook.dto.inline.Sup;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;
import org.tehlab.whitek0t.fictionbook.internal.reader.fb2.Fb2Reader;
import org.tehlab.whitek0t.fictionbook.internal.writer.fb2.Fb2Writer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based round-trip: на случайно сгенерированных деревьях проверяем главный
 * инвариант «прощающее чтение / строгая запись» — <b>фикспоинт</b>:
 * {@code write(read(write(book)))} обязан давать байт-в-байт тот же XML, что и
 * {@code write(book)}.
 * <p>
 * Сравниваем именно второй проход с первым (а не DTO с DTO), поэтому генератор может
 * выдавать и «грязные» книги: первая запись их канонизирует санитайзерами, а нас
 * интересует стабильность уже канонической формы. Любой дрейф (потеря узла,
 * перестановка атрибутов, схлопывание/раздувание текста) роняет тест.
 * <p>
 * Генерируется тело (секции с вложенностью, параграфы, инлайн-форматирование, ссылки)
 * и бинарные ресурсы. {@code description} фиксирован минимальным — его round-trip
 * подробно покрыт примерами в {@link Fb2RoundTripTest}.
 */
class Fb2RoundTripPropertyTest {

    /** Латиница, кириллица, цифры, пунктуация и XML-спецсимволы — без пробелов. */
    private static final String TEXT_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJ"
            + "0123456789.,!?-:;()"
            + "<>&\"'"
            + "абвгдеёжзиклмнопрстуфхцчшщ";

    private static final Description DESCRIPTION = new Description(
            new TitleInfo(
                    List.of(new Author("Имя", null, "Фамилия")),
                    List.of("prose_classic"),
                    "Заголовок",
                    List.of(),               // без annotation
                    "ru", null, null, List.of()
            ),
            new DocumentInfo(
                    List.of(), null, null, null, null, "doc-1", "1.0", List.of()
            ),
            null                              // без publish-info
    );

    // ========================================================================
    // ИНВАРИАНТ
    // ========================================================================

    @Property(tries = 300)
    void writeReadWriteIsAlwaysAFixpoint(@ForAll("books") FictionBookDto book) throws Exception {
        byte[] firstPass = write(book);
        byte[] secondPass = write(read(firstPass));

        assertThat(new String(secondPass, StandardCharsets.UTF_8))
                .isEqualTo(new String(firstPass, StandardCharsets.UTF_8));
    }

    // ========================================================================
    // ХЕЛПЕРЫ I/O
    // ========================================================================

    private static byte[] write(FictionBookDto book) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Fb2Writer().write(book, baos);
        return baos.toByteArray();
    }

    private static FictionBookDto read(byte[] fb2) throws Exception {
        return new Fb2Reader().read(new ByteArrayInputStream(fb2));
    }

    // ========================================================================
    // ГЕНЕРАТОРЫ
    // ========================================================================

    @Provide
    Arbitrary<FictionBookDto> books() {
        Arbitrary<BodyDto> mainBody = sectionArb(2).list().ofMinSize(1).ofMaxSize(2)
                .map(sections -> new BodyDto(null, sections));

        Arbitrary<List<BodyDto>> bodies = Combinators.combine(
                mainBody,
                sectionArb(1).map(s -> new BodyDto("notes", List.of(s))).optional()
        ).as((main, notes) -> notes
                .map(n -> List.of(main, n))
                .orElseGet(() -> List.of(main)));

        Arbitrary<Map<String, Resource>> resources = resourceArb().list().ofMaxSize(2)
                .map(list -> {
                    Map<String, Resource> map = new LinkedHashMap<>();
                    for (Resource r : list) {
                        map.put(r.id(), r);
                    }
                    return map;
                });

        return Combinators.combine(bodies, resources)
                .as((bs, rs) -> new FictionBookDto(DESCRIPTION, bs, rs));
    }

    private Arbitrary<Section> sectionArb(int depth) {
        Arbitrary<String> id = Arbitraries.oneOf(Arbitraries.just(null), wordArb());
        Arbitrary<List<BlockElement>> title = Arbitraries.oneOf(
                Arbitraries.just(List.of()),
                textArb().map(t -> List.of(new Paragraph(List.of(new Text(t)))))
        );
        Arbitrary<List<BlockElement>> content = blockArb().list().ofMinSize(1).ofMaxSize(3);

        if (depth <= 0) {
            return Combinators.combine(id, title, content)
                    .as((i, ti, c) -> new Section(i, ti, c, List.of(), Map.of()));
        }
        Arbitrary<List<Section>> subs = sectionArb(depth - 1).list().ofMaxSize(2);
        return Combinators.combine(id, title, content, subs)
                .as((i, ti, c, s) -> new Section(i, ti, c, s, Map.of()));
    }

    private Arbitrary<BlockElement> blockArb() {
        return paragraphArb().map(p -> (BlockElement) p);
    }

    private Arbitrary<Paragraph> paragraphArb() {
        return inlineArb(2).list().ofMinSize(1).ofMaxSize(4).map(Paragraph::new);
    }

    private Arbitrary<InlineElement> inlineArb(int depth) {
        Arbitrary<InlineElement> text = textArb().map(t -> (InlineElement) new Text(t));
        Arbitrary<InlineElement> link = Combinators.combine(hrefArb(), textArb())
                .as((h, t) -> (InlineElement) new Link(h, null, List.of(new Text(t))));
        Arbitrary<InlineElement> sub = textArb().map(t -> (InlineElement) new Sub(List.of(new Text(t))));
        Arbitrary<InlineElement> sup = textArb().map(t -> (InlineElement) new Sup(List.of(new Text(t))));

        if (depth <= 0) {
            return Arbitraries.oneOf(text, text, text, link, sub, sup);
        }
        Arbitrary<List<InlineElement>> kids = inlineArb(depth - 1).list().ofMinSize(1).ofMaxSize(3);
        Arbitrary<InlineElement> strong = kids.map(k -> (InlineElement) new Strong(k));
        Arbitrary<InlineElement> emphasis = kids.map(k -> (InlineElement) new Emphasis(k));

        // text взвешен выше, чтобы деревья не были сплошь вложенными контейнерами.
        return Arbitraries.oneOf(text, text, text, link, sub, sup, strong, emphasis);
    }

    private Arbitrary<Resource> resourceArb() {
        Arbitrary<String> contentType = Arbitraries.of("image/jpeg", "image/png");
        Arbitrary<byte[]> data = Arbitraries.integers().between(0, 255)
                .list().ofMinSize(1).ofMaxSize(16)
                .map(ints -> {
                    byte[] bytes = new byte[ints.size()];
                    for (int i = 0; i < bytes.length; i++) {
                        bytes[i] = ints.get(i).byteValue();
                    }
                    return bytes;
                });
        return Combinators.combine(wordArb(), contentType, data)
                .as((id, ct, d) -> new Resource(id, ct, () -> new ByteArrayInputStream(d)));
    }

    private Arbitrary<String> textArb() {
        return Arbitraries.strings().withChars(TEXT_CHARS).ofMinLength(1).ofMaxLength(12);
    }

    /** Валидный NCName-токен — годится и как id секции, и как id бинарника. */
    private Arbitrary<String> wordArb() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(8);
    }

    private Arbitrary<String> hrefArb() {
        return Arbitraries.oneOf(
                wordArb().map(w -> "#" + w),
                wordArb().map(w -> "http://example.com/" + w)
        );
    }
}
