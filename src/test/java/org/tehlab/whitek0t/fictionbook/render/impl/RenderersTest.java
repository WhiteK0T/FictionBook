package org.tehlab.whitek0t.fictionbook.render.impl;

import org.junit.jupiter.api.Test;
import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.description.*;
import org.tehlab.whitek0t.fictionbook.dto.inline.*;
import org.tehlab.whitek0t.fictionbook.render.BookPlayer;
import org.tehlab.whitek0t.fictionbook.render.ResourceResolver;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RenderersTest {

    @Test
    void htmlRendererShouldEscapeSpecialChars() {
        HtmlRenderer renderer = new HtmlRenderer();
        BookPlayer player = new BookPlayer(renderer);

        player.play(createBookWithParagraph("Текст <с> тегами & \"кавычками\""));

        String html = renderer.getOutput();
        assertThat(html).contains("Текст &lt;с&gt; тегами &amp; &quot;кавычками&quot;");
    }

    @Test
    void htmlRendererShouldRenderBoldAndItalic() {
        HtmlRenderer renderer = new HtmlRenderer();
        BookPlayer player = new BookPlayer(renderer);

        Paragraph para = new Paragraph(List.of(
                new Text("Обычный "),
                new Strong(List.of(new Text("жирный"))),
                new Text(" и "),
                new Emphasis(List.of(new Text("курсив")))
        ));

        player.play(createBookWithParagraph(para));

        String html = renderer.getOutput();
        assertThat(html).contains("<strong>жирный</strong>");
        assertThat(html).contains("<em>курсив</em>");
    }

    @Test
    void htmlRendererShouldRenderLinks() {
        HtmlRenderer renderer = new HtmlRenderer();
        BookPlayer player = new BookPlayer(renderer);

        Link link = new Link("#note1", "note", List.of(new Text("[1]")));
        player.play(createBookWithParagraph(new Paragraph(List.of(link))));

        String html = renderer.getOutput();
        assertThat(html).contains("<a href=\"#note1\">[1]</a>");
    }

    @Test
    void htmlRendererShouldOpenExternalLinksInNewTab() {
        HtmlRenderer renderer = new HtmlRenderer();
        BookPlayer player = new BookPlayer(renderer);

        Link link = new Link("https://example.com", null, List.of(new Text("Ссылка")));
        player.play(createBookWithParagraph(new Paragraph(List.of(link))));

        String html = renderer.getOutput();
        assertThat(html).contains("target=\"_blank\"");
        assertThat(html).contains("rel=\"noopener noreferrer\"");
    }

    @Test
    void htmlRendererShouldUseResourceResolver() {
        ResourceResolver resolver = (resource, alt) -> "images/" + resource.id() + ".jpg";
        HtmlRenderer renderer = HtmlRenderer.builder()
                .resourceResolver(resolver)
                .build();

        Resource cover = new Resource("cover", "image/jpeg",
                () -> new java.io.ByteArrayInputStream(new byte[0]));

        BookPlayer player = new BookPlayer(renderer,
                href -> "cover".equals(href.replace("#", "")) ? cover : null);

        Paragraph para = new Paragraph(List.of(new ImageRef("#cover", "Обложка")));
        player.play(createBookWithParagraph(para));

        String html = renderer.getOutput();
        assertThat(html).contains("src=\"images/cover.jpg\"");
        assertThat(html).contains("alt=\"Обложка\"");
    }

    @Test
    void htmlRendererShouldWrapInDocument() {
        HtmlRenderer renderer = HtmlRenderer.builder()
                .wrapInHtmlDocument(true)
                .title("Моя книга")
                .build();

        new BookPlayer(renderer).play(createMinimalBook());

        String html = renderer.getOutput();
        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).contains("<title>Моя книга</title>");
        assertThat(html).contains("</html>");
        assertThat(html).contains("<style>");
    }

    @Test
    void htmlRendererShouldReturnFragmentWhenNotWrapped() {
        HtmlRenderer renderer = HtmlRenderer.builder()
                .wrapInHtmlDocument(false)
                .build();

        new BookPlayer(renderer).play(createMinimalBook());

        String html = renderer.getOutput();
        assertThat(html).doesNotContain("<!DOCTYPE html>");
        assertThat(html).doesNotContain("<head>");
    }

    @Test
    void htmlRendererShouldApplyParagraphStyles() {
        HtmlRenderer renderer = new HtmlRenderer();
        BookPlayer player = new BookPlayer(renderer);

        Section section = new Section("ch1",
                List.of(new Paragraph(List.of(new Text("Глава 1")))),
                List.of(new Paragraph(List.of(new Text("Текст")))),
                List.of(),
                Map.of());

        FictionBookDto book = new FictionBookDto(
                createMinimalDescription(),
                List.of(new BodyDto(null, List.of(section))),
                Map.of());

        player.play(book);

        String html = renderer.getOutput();
        assertThat(html).contains("<section id=\"ch1\">");
        assertThat(html).contains("class=\"section-title\"");
    }

    @Test
    void plainTextRendererShouldStripFormatting() {
        PlainTextRenderer renderer = new PlainTextRenderer();
        BookPlayer player = new BookPlayer(renderer);

        Paragraph para = new Paragraph(List.of(
                new Text("Обычный "),
                new Strong(List.of(new Text("жирный "))),
                new Emphasis(List.of(new Text("курсив")))
        ));

        player.play(createBookWithParagraph(para));

        String text = renderer.getOutput();
        assertThat(text).isEqualTo("Обычный жирный курсив");
    }

    @Test
    void plainTextRendererShouldCountWords() {
        PlainTextRenderer renderer = new PlainTextRenderer();
        BookPlayer player = new BookPlayer(renderer);

        player.play(createBookWithParagraph("Один два три четыре пять"));

        assertThat(renderer.getWordCount()).isEqualTo(5);
    }

    @Test
    void plainTextRendererShouldGeneratePreview() {
        PlainTextRenderer renderer = new PlainTextRenderer();
        BookPlayer player = new BookPlayer(renderer);

        player.play(createBookWithParagraph(
                "Это длинный текст для проверки функции превью, " +
                        "которая должна обрезать строку на границе слова."));

        String preview = renderer.getPreview(30);
        assertThat(preview.length()).isLessThanOrEqualTo(33); // 30 + "..."
        assertThat(preview).endsWith("...");
    }

    @Test
    void plainTextRendererShouldIncludeImageAlt() {
        PlainTextRenderer renderer = new PlainTextRenderer(true, true, "\n");
        BookPlayer player = new BookPlayer(renderer);

        Paragraph para = new Paragraph(List.of(
                new Text("До картинки "),
                new ImageRef("#img", "Описание картинки"),
                new Text(" после")
        ));

        player.play(createBookWithParagraph(para));

        String text = renderer.getOutput();
        assertThat(text).contains("[Описание картинки]");
    }

    @Test
    void plainTextRendererShouldSkipImageAltWhenDisabled() {
        PlainTextRenderer renderer = new PlainTextRenderer(false, true, "\n");
        BookPlayer player = new BookPlayer(renderer);

        Paragraph para = new Paragraph(List.of(new ImageRef("#img", "Описание")));
        player.play(createBookWithParagraph(para));

        String text = renderer.getOutput();
        assertThat(text).doesNotContain("Описание");
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private FictionBookDto createMinimalBook() {
        Section section = new Section(null, List.of(),
                List.of(new Paragraph(List.of(new Text("Hello")))),
                List.of(), Map.of());
        return new FictionBookDto(
                createMinimalDescription(),
                List.of(new BodyDto(null, List.of(section))),
                Map.of());
    }

    private FictionBookDto createBookWithParagraph(String text) {
        return createBookWithParagraph(new Paragraph(List.of(new Text(text))));
    }

    private FictionBookDto createBookWithParagraph(Paragraph para) {
        Section section = new Section(null, List.of(), List.of(para), List.of(), Map.of());
        return new FictionBookDto(
                createMinimalDescription(),
                List.of(new BodyDto(null, List.of(section))),
                Map.of());
    }

    private Description createMinimalDescription() {
        return new Description(
                new TitleInfo(List.of(), List.of(), "Test", List.of(), "ru", null, null, List.of()),
                new DocumentInfo(List.of(), null, null, null, null, "id", "1.0", List.of()),
                null
        );
    }
}
