package org.tehlab.whitek0t.fictionbook.internal.sanitizer;

import org.junit.jupiter.api.Test;
import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.inline.ImageRef;
import org.tehlab.whitek0t.fictionbook.dto.inline.Link;
import org.tehlab.whitek0t.fictionbook.dto.inline.Strong;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SanitizersTest {

    @Test
    void emptyParagraphCleanerShouldRemoveEmptyParagraphs() {
        Section section = new Section(null, List.of(),
                List.of(
                        new Paragraph(List.of()),
                        new Paragraph(List.of(new Text("Hello"))),
                        new Paragraph(List.of(new Text("   "))),
                        new Paragraph(List.of(new Text(""), new Text("")))
                ),
                List.of(), Map.of());

        FictionBookDto book = createBook(section);

        FictionBookDto clean = new EmptyParagraphCleaner().sanitize(book);

        Section cleanedSection = clean.bodies().get(0).sections().get(0);
        assertThat(cleanedSection.content()).hasSize(1);
        assertThat(((Paragraph) cleanedSection.content().get(0)).elements())
                .containsExactly(new Text("Hello"));
    }

    @Test
    void emptyParagraphCleanerShouldKeepParagraphsWithImages() {
        Section section = new Section(null, List.of(),
                List.of(new Paragraph(List.of(new ImageRef("#cover", null)))),
                List.of(), Map.of());

        FictionBookDto book = createBook(section);
        FictionBookDto clean = new EmptyParagraphCleaner().sanitize(book);

        assertThat(clean.bodies().get(0).sections().get(0).content()).hasSize(1);
    }

    @Test
    void textNodeMergerShouldMergeAdjacentTexts() {
        Section section = new Section(null, List.of(),
                List.of(new Paragraph(List.of(
                        new Text("Hello "),
                        new Text("world"),
                        new Strong(List.of(new Text("!"))),
                        new Text(" How "),
                        new Text("are "),
                        new Text("you?")
                ))),
                List.of(), Map.of());

        FictionBookDto book = createBook(section);
        FictionBookDto clean = new TextNodeMerger().sanitize(book);

        Paragraph p = (Paragraph) clean.bodies().get(0).sections().get(0).content().get(0);
        assertThat(p.elements()).containsExactly(
                new Text("Hello world"),
                new Strong(List.of(new Text("!"))),
                new Text(" How are you?")
        );
    }

    @Test
    void orphanedImageCleanerShouldReplaceBrokenReferences() {
        Section section = new Section(null, List.of(),
                List.of(new Paragraph(List.of(new ImageRef("#missing", null)))),
                List.of(), Map.of());

        FictionBookDto book = new FictionBookDto(null,
                List.of(new BodyDto(null, List.of(section))),
                Map.of()); // Пустые ресурсы — все ссылки битые

        FictionBookDto clean = new OrphanedImageCleaner().sanitize(book);

        Paragraph p = (Paragraph) clean.bodies().get(0).sections().get(0).content().get(0);
        assertThat(p.elements()).containsExactly(new Text("[Image Missing: #missing]"));
    }

    @Test
    void orphanedImageCleanerShouldKeepValidReferences() {
        Section section = new Section(null, List.of(),
                List.of(new Paragraph(List.of(new ImageRef("#cover", null)))),
                List.of(), Map.of());

        Resource resource = new Resource("cover", "image/jpeg",
                () -> new java.io.ByteArrayInputStream(new byte[0]));

        FictionBookDto book = new FictionBookDto(null,
                List.of(new BodyDto(null, List.of(section))),
                Map.of("cover", resource));

        FictionBookDto clean = new OrphanedImageCleaner().sanitize(book);

        Paragraph p = (Paragraph) clean.bodies().get(0).sections().get(0).content().get(0);
        assertThat(p.elements()).containsExactly(new ImageRef("#cover", null));
    }

    @Test
    void emptySectionCleanerShouldRemoveEmptySections() {
        Section empty = new Section(null, List.of(), List.of(), List.of(), Map.of());
        Section filled = new Section("ch1", List.of(),
                List.of(new Paragraph(List.of(new Text("Content")))),
                List.of(), Map.of());

        FictionBookDto book = new FictionBookDto(null,
                List.of(new BodyDto(null, List.of(empty, filled))),
                Map.of());

        FictionBookDto clean = new EmptySectionCleaner().sanitize(book);

        assertThat(clean.bodies().get(0).sections()).hasSize(1);
        assertThat(clean.bodies().get(0).sections().get(0).id()).isEqualTo("ch1");
    }

    @Test
    void attributeNormalizerShouldFixInvalidIds() {
        Section section = new Section("123 invalid id!", List.of(),
                List.of(new Paragraph(List.of(new Text("Content")))),
                List.of(), Map.of());

        FictionBookDto book = createBook(section);
        FictionBookDto clean = new AttributeNormalizer().sanitize(book);

        String newId = clean.bodies().get(0).sections().get(0).id();
        assertThat(newId).isEqualTo("_23_invalid_id_");
    }

    @Test
    void attributeNormalizerShouldTrimAndRemoveBlankIds() {
        Section s1 = new Section("  valid_id  ", List.of(), List.of(), List.of(), Map.of());
        Section s2 = new Section("   ", List.of(), List.of(), List.of(), Map.of());
        Section s3 = new Section("", List.of(), List.of(), List.of(), Map.of());

        FictionBookDto book = new FictionBookDto(null,
                List.of(new BodyDto(null, List.of(s1, s2, s3))),
                Map.of());

        FictionBookDto clean = new AttributeNormalizer().sanitize(book);

        assertThat(clean.bodies().get(0).sections().get(0).id()).isEqualTo("valid_id");
        assertThat(clean.bodies().get(0).sections().get(1).id()).isNull();
        assertThat(clean.bodies().get(0).sections().get(2).id()).isNull();
    }

    @Test
    void sanitizerPipelineShouldApplyAllSanitizers() {
        Section section = new Section("123", List.of(),
                List.of(
                        new Paragraph(List.of()),
                        new Paragraph(List.of(new Text("Hello "), new Text("world"))),
                        new Paragraph(List.of(new ImageRef("#missing", null)))
                ),
                List.of(), Map.of());

        FictionBookDto book = new FictionBookDto(null,
                List.of(new BodyDto(null, List.of(section))),
                Map.of());

        FictionBookDto clean = SanitizerPipeline.standard().sanitize(book);

        Section cleanedSection = clean.bodies().get(0).sections().get(0);

        // ID нормализован
        assertThat(cleanedSection.id()).isEqualTo("_23");

        // Пустой параграф удалён, остались 2
        assertThat(cleanedSection.content()).hasSize(2);

        // Text-ноды склеены
        Paragraph p1 = (Paragraph) cleanedSection.content().get(0);
        assertThat(p1.elements()).containsExactly(new Text("Hello world"));

        // Битая ссылка на картинку заменена
        Paragraph p2 = (Paragraph) cleanedSection.content().get(1);
        assertThat(p2.elements()).containsExactly(new Text("[Image Missing: #missing]"));
    }

    @Test
    void sanitizersShouldBeIdempotent() {
        Section section = new Section("ch1", List.of(),
                List.of(new Paragraph(List.of(new Text("Text")))),
                List.of(), Map.of());

        FictionBookDto book = createBook(section);
        SanitizerPipeline pipeline = SanitizerPipeline.standard();

        FictionBookDto once = pipeline.sanitize(book);
        FictionBookDto twice = pipeline.sanitize(once);

        // Повторный запуск не должен ничего менять
        assertThat(twice).usingRecursiveComparison().isEqualTo(once);
    }

    @Test
    void orphanedLinkCleanerShouldKeepResolvableInternalLink() {
        // Ссылка #ch1 указывает на секцию с id="ch1" — должна резолвиться.
        Link link = new Link("#ch1", null, List.of(new Text("see chapter")));
        Section section = new Section("ch1", List.of(),
                List.of(new Paragraph(List.of(link))),
                List.of(), Map.of());

        FictionBookDto book = createBook(section);
        FictionBookDto clean = new OrphanedLinkCleaner().sanitize(book);

        Paragraph p = (Paragraph) clean.bodies().get(0).sections().get(0).content().get(0);
        assertThat(p.elements()).containsExactly(link);
    }

    @Test
    void orphanedLinkCleanerShouldMarkBrokenLinkWithEmptyText() {
        // Битая ссылка без текста — подставляется маркер [broken: ...].
        Section section = new Section("ch1", List.of(),
                List.of(new Paragraph(List.of(new Link("#missing", null, List.of())))),
                List.of(), Map.of());

        FictionBookDto book = createBook(section);
        FictionBookDto clean = new OrphanedLinkCleaner().sanitize(book);

        Paragraph p = (Paragraph) clean.bodies().get(0).sections().get(0).content().get(0);
        assertThat(p.elements()).containsExactly(
                new Link("#missing", null, List.of(new Text("[broken: #missing]"))));
    }

    @Test
    void orphanedLinkCleanerShouldKeepBrokenLinkTextWhenNotEmpty() {
        // Битая ссылка, но с текстом — текст не теряем, ссылку оставляем как есть.
        Link link = new Link("#missing", null, List.of(new Text("click here")));
        Section section = new Section("ch1", List.of(),
                List.of(new Paragraph(List.of(link))),
                List.of(), Map.of());

        FictionBookDto book = createBook(section);
        FictionBookDto clean = new OrphanedLinkCleaner().sanitize(book);

        Paragraph p = (Paragraph) clean.bodies().get(0).sections().get(0).content().get(0);
        assertThat(p.elements()).containsExactly(link);
    }

    @Test
    void orphanedLinkCleanerShouldIgnoreExternalLinks() {
        // Внешняя ссылка (без #) не трогается, даже если текст пустой.
        Link link = new Link("https://example.com", null, List.of());
        Section section = new Section("ch1", List.of(),
                List.of(new Paragraph(List.of(link))),
                List.of(), Map.of());

        FictionBookDto book = createBook(section);
        FictionBookDto clean = new OrphanedLinkCleaner().sanitize(book);

        Paragraph p = (Paragraph) clean.bodies().get(0).sections().get(0).content().get(0);
        assertThat(p.elements()).containsExactly(link);
    }

    @Test
    void orphanedLinkCleanerShouldIgnoreBlankHref() {
        // Пустой href не считается ссылкой — оставляем без изменений.
        Link link = new Link("", null, List.of());
        Section section = new Section("ch1", List.of(),
                List.of(new Paragraph(List.of(link))),
                List.of(), Map.of());

        FictionBookDto book = createBook(section);
        FictionBookDto clean = new OrphanedLinkCleaner().sanitize(book);

        Paragraph p = (Paragraph) clean.bodies().get(0).sections().get(0).content().get(0);
        assertThat(p.elements()).containsExactly(link);
    }

    private FictionBookDto createBook(Section section) {
        return new FictionBookDto(null,
                List.of(new BodyDto(null, List.of(section))),
                Map.of());
    }
}