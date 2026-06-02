package org.tehlab.whitek0t.fictionbook.internal.parser.stax;

import org.junit.jupiter.api.Test;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.block.TableCell;
import org.tehlab.whitek0t.fictionbook.dto.inline.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NodeBuildersTest {

    @Test
    void paragraphBuilderShouldMergeTextChunks() {
        ParagraphBuilder builder = new ParagraphBuilder();
        builder.appendText("Hello ");
        builder.appendText("world");

        Paragraph result = (Paragraph) builder.build();

        assertThat(result.elements())
                .containsExactly(new Text("Hello world"));
    }

    @Test
    void paragraphBuilderShouldHandleMixedContent() {
        ParagraphBuilder builder = new ParagraphBuilder();
        builder.appendText("Before ");

        InlineContainerBuilder<Strong> strong = new InlineContainerBuilder<>(Strong::new);
        strong.appendText("bold");
        builder.addChild(strong.build());

        builder.appendText(" after");

        Paragraph result = (Paragraph) builder.build();

        assertThat(result.elements()).containsExactly(
                new Text("Before "),
                new Strong(List.of(new Text("bold"))),
                new Text(" after")
        );
    }

    @Test
    void inlineContainerBuilderShouldNestElements() {
        InlineContainerBuilder<Emphasis> builder = new InlineContainerBuilder<>(Emphasis::new);
        builder.appendText("italic ");

        InlineContainerBuilder<Strong> nested = new InlineContainerBuilder<>(Strong::new);
        nested.appendText("bold");
        builder.addChild(nested.build());

        builder.appendText(" text");

        Emphasis result = (Emphasis) builder.build();

        assertThat(result.elements()).containsExactly(
                new Text("italic "),
                new Strong(List.of(new Text("bold"))),
                new Text(" text")
        );
    }

    @Test
    void linkBuilderShouldCreateLinkWithHref() {
        LinkBuilder builder = new LinkBuilder("#note1", "note");
        builder.appendText("[1]");

        Link result = (Link) builder.build();

        assertThat(result.href()).isEqualTo("#note1");
        assertThat(result.type()).isEqualTo("note");
        assertThat(result.elements()).containsExactly(new Text("[1]"));
    }

    @Test
    void linkBuilderShouldUseHrefAsTextIfEmpty() {
        LinkBuilder builder = new LinkBuilder("http://example.com", null);

        Link result = (Link) builder.build();

        assertThat(result.href()).isEqualTo("http://example.com");
        assertThat(result.elements()).containsExactly(new Text("http://example.com"));
    }

    @Test
    void imageBuilderShouldCreateImageRef() {
        ImageBuilder builder = new ImageBuilder("#cover", "Обложка");

        ImageRef result = (ImageRef) builder.build();

        assertThat(result.href()).isEqualTo("#cover");
        assertThat(result.alt()).isEqualTo("Обложка");
    }

    @Test
    void ignoreBuilderShouldReturnNull() {
        IgnoreBuilder builder = new IgnoreBuilder();
        builder.appendText("ignored text");
        builder.addChild(new Text("ignored child"));

        assertThat(builder.build()).isNull();
    }

    @Test
    void tableCellBuilderShouldWrapOrphanTextInParagraph() {
        TableCellBuilder builder = new TableCellBuilder();
        builder.appendText("Dirty text");

        TableCell result = (TableCell) builder.build();

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0))
                .isInstanceOf(Paragraph.class)
                .extracting(p -> ((Paragraph) p).elements())
                .asList()
                .containsExactly(new Text("Dirty text"));
    }

    @Test
    void tableCellBuilderShouldAcceptExplicitParagraphs() {
        TableCellBuilder builder = new TableCellBuilder();

        ParagraphBuilder para = new ParagraphBuilder();
        para.appendText("Clean text");
        builder.addChild(para.build());

        TableCell result = (TableCell) builder.build();

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(Paragraph.class);
    }

    @Test
    void tableCellBuilderShouldHandleMixedContent() {
        TableCellBuilder builder = new TableCellBuilder();

        // Сначала "грязный" текст
        builder.appendText("Dirty ");

        // Потом явный параграф
        ParagraphBuilder para = new ParagraphBuilder();
        para.appendText("Clean");
        builder.addChild(para.build());

        // Снова "грязный" текст
        builder.appendText(" More dirty");

        TableCell result = (TableCell) builder.build();

        // Ожидаем 3 параграфа: неявный, явный, неявный
        assertThat(result.content()).hasSize(3);
        assertThat(result.content().get(0)).isInstanceOf(Paragraph.class);
        assertThat(result.content().get(1)).isInstanceOf(Paragraph.class);
        assertThat(result.content().get(2)).isInstanceOf(Paragraph.class);
    }

    @Test
    void verseBuilderShouldWorkLikeParagraph() {
        VerseBuilder builder = new VerseBuilder();
        builder.appendText("Я помню ");

        InlineContainerBuilder<Emphasis> em = new InlineContainerBuilder<>(Emphasis::new);
        em.appendText("чудное");
        builder.addChild(em.build());

        builder.appendText(" мгновенье");

        var result = builder.build();

        assertThat(result).isInstanceOf(org.tehlab.whitek0t.fictionbook.dto.block.Verse.class);
    }
}