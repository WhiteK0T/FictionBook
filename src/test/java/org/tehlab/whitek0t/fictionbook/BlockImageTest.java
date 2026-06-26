package org.tehlab.whitek0t.fictionbook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;
import org.tehlab.whitek0t.fictionbook.dto.block.BlockImage;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.internal.reader.fb2.Fb2Reader;
import org.tehlab.whitek0t.fictionbook.internal.sanitizer.OrphanedImageCleaner;
import org.tehlab.whitek0t.fictionbook.internal.writer.fb2.Fb2Writer;
import org.tehlab.whitek0t.fictionbook.render.BookPlayer;
import org.tehlab.whitek0t.fictionbook.render.ResourceResolver;
import org.tehlab.whitek0t.fictionbook.render.impl.HtmlRenderer;
import org.tehlab.whitek0t.fictionbook.render.impl.PlainTextRenderer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты блочной картинки {@link BlockImage}: разбор {@code <image>} как прямого
 * ребёнка {@code <section>}, round-trip через writer, рендеринг (HTML/текст) и
 * чистка битых ссылок санитайзером.
 */
@DisplayName("BlockImage Tests")
class BlockImageTest {

    // 1x1 PNG
    private static final String PNG_1X1 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M8AAAMBAQDJ/pLvAAAAAElFTkSuQmCC";

    /** FB2 с блочной картинкой; {@code coverId} — id существующего бинарника. */
    private static String fb2(String imageHref, boolean withBinary) {
        String binary = withBinary
                ? "  <binary id=\"pic.png\" content-type=\"image/png\">" + PNG_1X1 + "</binary>\n"
                : "";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" \
                xmlns:l="http://www.w3.org/1999/xlink">
                  <description>
                    <title-info>
                      <genre>prose</genre>
                      <book-title>Книга с иллюстрацией</book-title>
                      <lang>ru</lang>
                    </title-info>
                  </description>
                  <body>
                    <section id="s1">
                      <p>До иллюстрации.</p>
                      <image l:href="%s" alt="Схема"/>
                      <p>После иллюстрации.</p>
                    </section>
                  </body>
                %s</FictionBook>
                """.formatted(imageHref, binary);
    }

    private static FictionBookDto read(String fb2) throws FictionBookException {
        return new Fb2Reader().read(
                new ByteArrayInputStream(fb2.getBytes(StandardCharsets.UTF_8)));
    }

    private static String writeToString(FictionBookDto book) throws FictionBookException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new Fb2Writer().write(book, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Resource lookup(FictionBookDto book, String href) {
        if (href == null) return null;
        String key = href.startsWith("#") ? href.substring(1) : href;
        return book.resources().get(key);
    }

    @Nested
    @DisplayName("Разбор")
    class Parsing {

        @Test
        @DisplayName("<image> в секции читается как BlockImage с href и alt")
        void parsesBlockImage() throws Exception {
            FictionBookDto book = read(fb2("#pic.png", true));
            Section section = book.bodies().getFirst().sections().getFirst();

            // 3 блока: <p>, <image>, <p>
            assertThat(section.content()).hasSize(3);
            assertThat(section.content().get(1)).isInstanceOf(BlockImage.class);

            BlockImage img = (BlockImage) section.content().get(1);
            assertThat(img.href()).isEqualTo("#pic.png");
            assertThat(img.alt()).isEqualTo("Схема");
        }
    }

    @Nested
    @DisplayName("Round-trip")
    class RoundTrip {

        @Test
        @DisplayName("блочная картинка сохраняется при записи и повторном чтении")
        void survivesRoundTrip() throws Exception {
            FictionBookDto book = read(fb2("#pic.png", true));

            String written = writeToString(book);
            assertThat(written).contains("<image l:href=\"#pic.png\" alt=\"Схема\"/>");

            FictionBookDto reread = read(written);
            BlockElement block = reread.bodies().getFirst().sections().getFirst().content().get(1);
            assertThat(block).isInstanceOf(BlockImage.class);
            assertThat(((BlockImage) block).href()).isEqualTo("#pic.png");
        }

        @Test
        @DisplayName("повторная запись байт-в-байт (фикспоинт)")
        void writeIsIdempotent() throws Exception {
            FictionBookDto book = read(fb2("#pic.png", true));
            String first = writeToString(book);
            String second = writeToString(read(first));
            assertThat(second).isEqualTo(first);
        }
    }

    @Nested
    @DisplayName("Рендеринг")
    class Rendering {

        @Test
        @DisplayName("HTML: блочная картинка превращается в <img> с base64")
        void rendersHtml() throws Exception {
            FictionBookDto book = read(fb2("#pic.png", true));

            HtmlRenderer renderer = HtmlRenderer.builder()
                    .resourceResolver(ResourceResolver.base64DataUri())
                    .build();
            new BookPlayer(renderer, href -> lookup(book, href)).play(book);

            String html = renderer.getOutput();
            assertThat(html).contains("<img", "src=\"data:image/png;base64,", "alt=\"Схема\"");
        }

        @Test
        @DisplayName("PlainText: выводится alt-текст блочной картинки")
        void rendersText() throws Exception {
            FictionBookDto book = read(fb2("#pic.png", true));

            PlainTextRenderer renderer = new PlainTextRenderer();
            new BookPlayer(renderer, href -> lookup(book, href)).play(book);

            assertThat(renderer.getOutput()).contains("[Схема]");
        }
    }

    @Nested
    @DisplayName("Санитайзер")
    class Sanitizing {

        @Test
        @DisplayName("битая ссылка блочной картинки заменяется на параграф-заглушку")
        void cleansOrphanedBlockImage() throws Exception {
            // Картинка ссылается на #ghost.png, бинарника нет.
            FictionBookDto book = read(fb2("#ghost.png", false));

            FictionBookDto cleaned = new OrphanedImageCleaner().sanitize(book);
            var content = cleaned.bodies().getFirst().sections().getFirst().content();

            // BlockImage заменён на Paragraph с текстом-заглушкой.
            assertThat(content).noneMatch(b -> b instanceof BlockImage);
            assertThat(content.get(1)).isInstanceOf(Paragraph.class);

            PlainTextRenderer renderer = new PlainTextRenderer();
            new BookPlayer(renderer).play(cleaned);
            assertThat(renderer.getOutput()).contains("[Image Missing: #ghost.png]");
        }
    }
}
