package org.tehlab.whitek0t.fictionbook.render.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;
import org.tehlab.whitek0t.fictionbook.dto.block.BlockImage;
import org.tehlab.whitek0t.fictionbook.dto.block.Cite;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.block.Table;
import org.tehlab.whitek0t.fictionbook.dto.block.TableCell;
import org.tehlab.whitek0t.fictionbook.dto.block.TableRow;
import org.tehlab.whitek0t.fictionbook.dto.inline.Emphasis;
import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Link;
import org.tehlab.whitek0t.fictionbook.dto.inline.Strikethrough;
import org.tehlab.whitek0t.fictionbook.dto.inline.Strong;
import org.tehlab.whitek0t.fictionbook.dto.inline.Sub;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;
import org.tehlab.whitek0t.fictionbook.render.BookPlayer;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты {@link MarkdownRenderer}: inline-разметка, заголовки по глубине секций,
 * blockquote, таблицы GFM, картинки/ссылки и экранирование.
 */
@DisplayName("MarkdownRenderer Tests")
class MarkdownRendererTest {

    private static Paragraph p(InlineElement... elems) {
        return new Paragraph(List.of(elems));
    }

    private static String renderBlock(BlockElement block) {
        MarkdownRenderer r = new MarkdownRenderer();
        new BookPlayer(r).playBlock(block);
        // getOutput() добавляет завершающий '\n' (для файлов) — в exact-match тестах убираем.
        return r.getOutput().strip();
    }

    @Nested
    @DisplayName("Inline-разметка")
    class Inline {

        @Test
        @DisplayName("strong/emphasis/strikethrough → ** / * / ~~")
        void formatting() {
            String md = renderBlock(p(
                    new Text("Текст "),
                    new Strong(List.of(new Text("жирный"))),
                    new Text(" и "),
                    new Emphasis(List.of(new Text("курсив"))),
                    new Text(", "),
                    new Strikethrough(List.of(new Text("зачёркнутый")))));
            assertThat(md).isEqualTo("Текст **жирный** и *курсив*, ~~зачёркнутый~~");
        }

        @Test
        @DisplayName("sub/sup → inline-HTML")
        void subSup() {
            String md = renderBlock(p(new Text("H"), new Sub(List.of(new Text("2"))), new Text("O")));
            assertThat(md).isEqualTo("H<sub>2</sub>O");
        }

        @Test
        @DisplayName("ссылка → [текст](href)")
        void link() {
            String md = renderBlock(p(
                    new Text("см. "),
                    new Link("#ch2", null, List.of(new Text("главу 2")))));
            assertThat(md).isEqualTo("см. [главу 2](#ch2)");
        }

        @Test
        @DisplayName("спецсимволы Markdown экранируются")
        void escaping() {
            String md = renderBlock(p(new Text("a*b_c[d]e`f")));
            assertThat(md).isEqualTo("a\\*b\\_c\\[d\\]e\\`f");
        }
    }

    @Nested
    @DisplayName("Блоки")
    class Blocks {

        @Test
        @DisplayName("заголовок секции → # , вложенная → ##")
        void headingsByDepth() {
            Section sub = new Section("s2",
                    List.of(p(new Text("Подглава"))),
                    List.of(p(new Text("текст"))),
                    List.of(), Map.of());
            Section top = new Section("s1",
                    List.of(p(new Text("Глава"))),
                    List.of(),
                    List.of(sub), Map.of());

            String md = renderBlock(top);
            assertThat(md).contains("# Глава");
            assertThat(md).contains("## Подглава");
        }

        @Test
        @DisplayName("цитата → blockquote")
        void citation() {
            Cite cite = new Cite(null, List.of(p(new Text("Мудрая мысль."))), "Автор");
            String md = renderBlock(cite);
            assertThat(md).contains("> Мудрая мысль.");
            assertThat(md).contains("*Автор*");
        }

        @Test
        @DisplayName("таблица → GFM с разделителем заголовка")
        void table() {
            TableRow head = new TableRow(List.of(
                    new TableCell(List.of(p(new Text("A")))),
                    new TableCell(List.of(p(new Text("B"))))));
            TableRow row = new TableRow(List.of(
                    new TableCell(List.of(p(new Text("1")))),
                    new TableCell(List.of(p(new Text("2"))))));
            String md = renderBlock(new Table(List.of(head, row)));

            assertThat(md).contains("| A | B |");
            assertThat(md).contains("| --- | --- |");
            assertThat(md).contains("| 1 | 2 |");
        }
    }

    @Nested
    @DisplayName("Картинки")
    class Images {

        @Test
        @DisplayName("с резолвером → ![alt](src)")
        void withResolver() {
            Resource res = new Resource("cover", "image/png",
                    () -> new ByteArrayInputStream(new byte[0]));
            MarkdownRenderer r = new MarkdownRenderer((resource, alt) -> "img/cover.png");
            new BookPlayer(r, href -> res).playBlock(new BlockImage("#cover", "Обложка"));

            assertThat(r.getOutput().strip()).isEqualTo("![Обложка](img/cover.png)");
        }

        @Test
        @DisplayName("без резолвера → текстовый placeholder")
        void withoutResolver() {
            String md = renderBlock(new BlockImage("#cover", "Обложка"));
            assertThat(md).isEqualTo("*(изображение: Обложка)*");
        }
    }
}
