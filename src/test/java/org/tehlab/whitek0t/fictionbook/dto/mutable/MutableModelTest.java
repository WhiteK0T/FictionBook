package org.tehlab.whitek0t.fictionbook.dto.mutable;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.ResourceDataProvider;
import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;
import org.tehlab.whitek0t.fictionbook.dto.block.Cite;
import org.tehlab.whitek0t.fictionbook.dto.block.EmptyLine;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.description.Author;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.dto.description.DocumentInfo;
import org.tehlab.whitek0t.fictionbook.dto.description.TitleInfo;
import org.tehlab.whitek0t.fictionbook.dto.inline.Emphasis;
import org.tehlab.whitek0t.fictionbook.dto.inline.Strong;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты изменяемой модели {@link MutableBook} / {@link MutableBody} /
 * {@link MutableSection}: round-trip с иммутабельным DTO и операции редактирования.
 */
class MutableModelTest {

    // ------------------------------------------------------------------
    // Фикстура: богатый DTO
    // ------------------------------------------------------------------

    private static Paragraph para(String text) {
        return new Paragraph(List.of(new Text(text)));
    }

    private static FictionBookDto richBook() {
        Description description = new Description(
                new TitleInfo(
                        List.of(new Author("Лев", null, "Толстой")),
                        List.of("prose"),
                        "Война и мир",
                        List.of(para("Аннотация")),
                        "ru", null, null, List.of("cover")),
                new DocumentInfo(List.of(), "tool", "2026-06-28", null, null,
                        "doc-1", "1.0", List.of()),
                null);

        // Глава 1 с разнородным содержимым и вложенной подсекцией
        Paragraph rich = new Paragraph(List.of(
                new Text("обычный "),
                new Strong(List.of(new Text("жирный"))),
                new Text(" и "),
                new Emphasis(List.of(new Text("курсив")))));
        Cite cite = new Cite("c1", List.of(para("цитата")), "автор цитаты");

        Section sub = new Section("ch1-1",
                List.of(para("Подглава")),
                List.of(para("текст подглавы")),
                List.of(),
                Map.of("class", "deep"));

        Section ch1 = new Section("ch1",
                List.of(para("Глава 1")),
                List.of(rich, new EmptyLine(), cite),
                List.of(sub),
                Map.of());

        Section ch2 = new Section("ch2",
                List.of(para("Глава 2")),
                List.of(para("конец")),
                List.of(),
                Map.of());

        BodyDto main = new BodyDto(null, List.of(ch1, ch2));

        Section note = new Section("n1", List.of(), List.of(para("сноска")),
                List.of(), Map.of());
        BodyDto notes = new BodyDto("notes", List.of(note));

        ResourceDataProvider provider = () -> new ByteArrayInputStream(new byte[]{1, 2, 3});
        Resource cover = new Resource("cover", "image/png", provider);

        // LinkedHashMap-порядок сохраняется конструктором FictionBookDto
        Map<String, Resource> resources = new java.util.LinkedHashMap<>();
        resources.put("cover", cover);

        return new FictionBookDto(description, List.of(main, notes), resources);
    }

    // ------------------------------------------------------------------

    @Nested
    class RoundTrip {

        @Test
        void fromThenToDtoIsStructurallyEqual() {
            FictionBookDto original = richBook();

            FictionBookDto restored = MutableBook.from(original).toDto();

            // Все узлы — records (value equality); Resource сохраняется по ссылке.
            assertThat(restored).isEqualTo(original);
        }

        @Test
        void preservesResourceOrder() {
            ResourceDataProvider p = () -> new ByteArrayInputStream(new byte[0]);
            Map<String, Resource> res = new java.util.LinkedHashMap<>();
            res.put("a", new Resource("a", "image/png", p));
            res.put("b", new Resource("b", "image/jpeg", p));
            res.put("c", new Resource("c", "image/gif", p));
            FictionBookDto dto = new FictionBookDto(null, List.of(), res);

            FictionBookDto restored = MutableBook.from(dto).toDto();

            assertThat(restored.resources().keySet()).containsExactly("a", "b", "c");
        }

        @Test
        void nullDescriptionSurvives() {
            FictionBookDto dto = new FictionBookDto(null,
                    List.of(new BodyDto(null, List.of())), Map.of());

            assertThat(MutableBook.from(dto).toDto().description()).isNull();
        }

        @Test
        void deepCopyIsIndependentOfOriginal() {
            FictionBookDto original = richBook();
            MutableBook book = MutableBook.from(original);

            // Правка изменяемой модели не должна затронуть исходный DTO
            book.mainBody().sections().clear();

            assertThat(original.bodies().getFirst().sections()).hasSize(2);
            assertThat(book.toDto().bodies().getFirst().sections()).isEmpty();
        }
    }

    @Nested
    class Editing {

        @Test
        void buildBookFromScratch() {
            MutableBook book = new MutableBook();
            book.mainBody().addSection(
                    MutableSection.withTitle("Глава 1")
                            .addParagraph("Первый абзац")
                            .addEmptyLine()
                            .addParagraph("Второй абзац"));

            FictionBookDto dto = book.toDto();

            BodyDto body = dto.bodies().getFirst();
            assertThat(body.name()).isNull();
            Section section = body.sections().getFirst();
            assertThat(section.title()).containsExactly(para("Глава 1"));
            assertThat(section.content()).containsExactly(
                    para("Первый абзац"), new EmptyLine(), para("Второй абзац"));
        }

        @Test
        void mainBodyOnEmptyBookCreatesBody() {
            MutableBook book = new MutableBook();

            MutableBody main = book.mainBody();

            assertThat(main).isNotNull();
            assertThat(book.bodies()).containsExactly(main);
        }

        @Test
        void addParagraphWithInlineFormatting() {
            Paragraph p = new Paragraph(List.of(
                    new Text("a "), new Strong(List.of(new Text("b")))));
            MutableSection s = new MutableSection("s").addParagraph(p);

            assertThat(s.toDto().content()).containsExactly(p);
        }

        @Test
        void liveListsAreReflectedInDto() {
            MutableBook book = MutableBook.from(richBook());
            List<BlockElement> content = book.findSection("ch2").orElseThrow().content();

            content.addFirst(para("вставка в начало"));

            Section ch2 = book.toDto().bodies().getFirst().sections().get(1);
            assertThat(((Paragraph) ch2.content().getFirst()).elements())
                    .containsExactly(new Text("вставка в начало"));
        }

        @Test
        void metadataIsPreserved() {
            MutableBook book = MutableBook.from(richBook());

            MutableSection sub = book.findSection("ch1-1").orElseThrow();

            assertThat(sub.metadata()).containsEntry("class", "deep");
            assertThat(book.toDto().bodies().getFirst().sections().getFirst()
                    .subSections().getFirst().metadata()).containsEntry("class", "deep");
        }

        @Test
        void putResourceAddsBinary() {
            MutableBook book = new MutableBook();
            ResourceDataProvider p = () -> new ByteArrayInputStream(new byte[]{9});
            book.putResource(new Resource("img", "image/png", p));

            assertThat(book.toDto().resources()).containsOnlyKeys("img");
        }
    }

    @Nested
    class FindAndRemove {

        @Test
        void findSectionTraversesSubSectionsAndBodies() {
            MutableBook book = MutableBook.from(richBook());

            assertThat(book.findSection("ch1-1")).isPresent();   // вложенная
            assertThat(book.findSection("n1")).isPresent();      // в теле notes
            assertThat(book.findSection("missing")).isEmpty();
            assertThat(book.findSection(null)).isEmpty();
        }

        @Test
        void removeSectionRemovesNestedFromParent() {
            MutableBook book = MutableBook.from(richBook());

            boolean removed = book.removeSection("ch1-1");

            assertThat(removed).isTrue();
            assertThat(book.findSection("ch1-1")).isEmpty();
            // Родитель остался, только без подсекции
            assertThat(book.findSection("ch1")).isPresent();
            assertThat(book.findSection("ch1").orElseThrow().subSections()).isEmpty();
        }

        @Test
        void removeSectionTopLevel() {
            MutableBook book = MutableBook.from(richBook());

            assertThat(book.removeSection("ch2")).isTrue();
            assertThat(book.toDto().bodies().getFirst().sections()).hasSize(1);
        }

        @Test
        void removeSectionMissingReturnsFalse() {
            MutableBook book = MutableBook.from(richBook());

            assertThat(book.removeSection("nope")).isFalse();
            assertThat(book.removeSection(null)).isFalse();
        }

        @Test
        void notesBodyLookup() {
            MutableBook book = MutableBook.from(richBook());

            assertThat(book.notesBody()).isNotNull();
            assertThat(book.notesBody().name()).isEqualTo("notes");
        }
    }

    @Nested
    class NullChecks {

        @Test
        void fromNullDtoThrows() {
            assertThatThrownBy(() -> MutableBook.from(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void addNullSectionThrows() {
            assertThatThrownBy(() -> new MutableBody().addSection(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void paragraphFactoryHandlesNullText() {
            assertThat(MutableBook.paragraph(null).elements()).isEmpty();
        }
    }
}
