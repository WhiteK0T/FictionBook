package org.tehlab.whitek0t.fictionbook.internal.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.Cite;
import org.tehlab.whitek0t.fictionbook.dto.block.Epigraph;
import org.tehlab.whitek0t.fictionbook.dto.block.Poem;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AnchorIndex Tests")
class AnchorIndexTest {

    @Nested
    @DisplayName("AnchorInfo")
    class AnchorInfoTests {

        @Test
        @DisplayName("should reject null or blank id")
        void shouldRejectNullOrBlankId() {
            assertThatThrownBy(() -> new AnchorInfo(null, "section", -1, -1, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AnchorInfo("   ", "section", -1, -1, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("hasByteOffset reflects offset presence")
        void hasByteOffset() {
            assertThat(new AnchorInfo("a", "section", -1, -1, null, null).hasByteOffset()).isFalse();
            assertThat(new AnchorInfo("a", "section", 0, -1, null, null).hasByteOffset()).isTrue();
            assertThat(new AnchorInfo("a", "section", 100, -1, null, null).hasByteOffset()).isTrue();
        }

        @Test
        @DisplayName("hasDomNode reflects domNode presence")
        void hasDomNode() {
            assertThat(new AnchorInfo("a", "section", -1, -1, null, null).hasDomNode()).isFalse();
            assertThat(new AnchorInfo("a", "section", -1, -1, null, new Object()).hasDomNode()).isTrue();
        }

        @Test
        @DisplayName("isNote is true only for the notes body")
        void isNote() {
            assertThat(new AnchorInfo("a", "section", -1, -1, "notes", null).isNote()).isTrue();
            assertThat(new AnchorInfo("a", "section", -1, -1, null, null).isNote()).isFalse();
            assertThat(new AnchorInfo("a", "section", -1, -1, "main", null).isNote()).isFalse();
        }
    }

    @Nested
    @DisplayName("Lookup")
    class LookupTests {

        private final AnchorInfo ch1 = new AnchorInfo("ch1", "section", -1, -1, null, null);
        private final AnchorInfo n1 = new AnchorInfo("n1", "note", 100, 5, "notes", new Object());
        private final AnchorIndex index = new AnchorIndex(Map.of("ch1", ch1, "n1", n1));

        @Test
        @DisplayName("find returns the anchor by raw id")
        void findExisting() {
            assertThat(index.find("ch1")).contains(ch1);
        }

        @Test
        @DisplayName("find returns empty for missing / null / blank id")
        void findMissing() {
            assertThat(index.find("missing")).isEmpty();
            assertThat(index.find(null)).isEmpty();
            assertThat(index.find("   ")).isEmpty();
        }

        @Test
        @DisplayName("resolve strips a leading '#'")
        void resolveInternal() {
            assertThat(index.resolve("#ch1")).contains(ch1);
        }

        @Test
        @DisplayName("resolve strips everything up to '#' for cross-file hrefs")
        void resolveCrossFile() {
            assertThat(index.resolve("notes.xml#n1")).contains(n1);
        }

        @Test
        @DisplayName("resolve returns empty for external links and null/blank")
        void resolveNonAnchor() {
            assertThat(index.resolve("http://example.com")).isEmpty();
            assertThat(index.resolve(null)).isEmpty();
            assertThat(index.resolve("")).isEmpty();
        }

        @Test
        @DisplayName("contains / canResolve")
        void containsAndCanResolve() {
            assertThat(index.contains("ch1")).isTrue();
            assertThat(index.contains("nope")).isFalse();
            assertThat(index.canResolve("#ch1")).isTrue();
            assertThat(index.canResolve("#nope")).isFalse();
            assertThat(index.canResolve("http://example.com")).isFalse();
        }

        @Test
        @DisplayName("size / isEmpty / getAll / getAllIds")
        void aggregates() {
            assertThat(index.size()).isEqualTo(2);
            assertThat(index.isEmpty()).isFalse();
            assertThat(index.getAllIds()).containsExactlyInAnyOrder("ch1", "n1");
            assertThat(index.getAll()).containsExactlyInAnyOrder(ch1, n1);
        }

        @Test
        @DisplayName("getAll / getAllIds are unmodifiable")
        void collectionsAreUnmodifiable() {
            assertThatThrownBy(() -> index.getAll().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> index.getAllIds().add("x"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("empty() index resolves nothing")
        void emptyIndex() {
            AnchorIndex empty = AnchorIndex.empty();
            assertThat(empty.isEmpty()).isTrue();
            assertThat(empty.size()).isZero();
            assertThat(empty.find("ch1")).isEmpty();
            assertThat(empty.canResolve("#ch1")).isFalse();
        }
    }

    @Nested
    @DisplayName("AnchorIndexBuilder.fromDto")
    class FromDtoTests {

        @Test
        @DisplayName("null book yields an empty index")
        void nullBook() {
            assertThat(AnchorIndexBuilder.fromDto(null).isEmpty()).isTrue();
        }

        @Test
        @DisplayName("registers section ids, including nested sub-sections")
        void registersSections() {
            Section sub = new Section("ch1-1", List.of(), List.of(), List.of(), Map.of());
            Section ch1 = new Section("ch1", List.of(), List.of(), List.of(sub), Map.of());
            FictionBookDto book = new FictionBookDto(null,
                    List.of(new BodyDto(null, List.of(ch1))), Map.of());

            AnchorIndex index = AnchorIndexBuilder.fromDto(book);

            assertThat(index.getAllIds()).containsExactlyInAnyOrder("ch1", "ch1-1");
            assertThat(index.find("ch1")).get()
                    .extracting(AnchorInfo::elementType).isEqualTo("section");
        }

        @Test
        @DisplayName("registers cite / epigraph / poem block ids")
        void registersBlocks() {
            Cite cite = new Cite("c1", List.of(), null);
            Epigraph epigraph = new Epigraph("e1", List.of(), null);
            Poem poem = new Poem("p1", List.of(), List.of(), List.of(), null);
            Section section = new Section("s1", List.of(),
                    List.of(cite, epigraph, poem), List.of(), Map.of());
            FictionBookDto book = new FictionBookDto(null,
                    List.of(new BodyDto(null, List.of(section))), Map.of());

            AnchorIndex index = AnchorIndexBuilder.fromDto(book);

            assertThat(index.getAllIds()).containsExactlyInAnyOrder("s1", "c1", "e1", "p1");
            assertThat(index.find("c1")).get().extracting(AnchorInfo::elementType).isEqualTo("cite");
            assertThat(index.find("e1")).get().extracting(AnchorInfo::elementType).isEqualTo("epigraph");
            assertThat(index.find("p1")).get().extracting(AnchorInfo::elementType).isEqualTo("poem");
        }

        @Test
        @DisplayName("registers binary resources as type 'binary'")
        void registersBinaries() {
            Resource cover = new Resource("cover", "image/jpeg",
                    () -> new ByteArrayInputStream(new byte[0]));
            FictionBookDto book = new FictionBookDto(null, List.of(), Map.of("cover", cover));

            AnchorIndex index = AnchorIndexBuilder.fromDto(book);

            assertThat(index.find("cover")).get()
                    .extracting(AnchorInfo::elementType).isEqualTo("binary");
        }

        @Test
        @DisplayName("propagates body name (notes) onto anchors")
        void propagatesBodyName() {
            Section note = new Section("n1", List.of(), List.of(), List.of(), Map.of());
            FictionBookDto book = new FictionBookDto(null,
                    List.of(new BodyDto("notes", List.of(note))), Map.of());

            AnchorInfo info = AnchorIndexBuilder.fromDto(book).find("n1").orElseThrow();

            assertThat(info.bodyName()).isEqualTo("notes");
            assertThat(info.isNote()).isTrue();
        }

        @Test
        @DisplayName("fromDto leaves byteOffset/lineNumber as -1 and domNode null")
        void offsetsArePlaceholders() {
            Section ch1 = new Section("ch1", List.of(), List.of(), List.of(), Map.of());
            FictionBookDto book = new FictionBookDto(null,
                    List.of(new BodyDto(null, List.of(ch1))), Map.of());

            AnchorInfo info = AnchorIndexBuilder.fromDto(book).find("ch1").orElseThrow();

            assertThat(info.byteOffset()).isEqualTo(-1);
            assertThat(info.lineNumber()).isEqualTo(-1);
            assertThat(info.domNode()).isNull();
            assertThat(info.hasByteOffset()).isFalse();
        }

        @Test
        @DisplayName("duplicate id is forgiving — keeps the first registration")
        void duplicateKeepsFirst() {
            // Бинарники регистрируются раньше секций, поэтому при коллизии id
            // выигрывает binary, а секция с тем же id пропускается.
            Resource dup = new Resource("dup", "image/png",
                    () -> new ByteArrayInputStream(new byte[0]));
            Section section = new Section("dup", List.of(), List.of(), List.of(), Map.of());
            FictionBookDto book = new FictionBookDto(null,
                    List.of(new BodyDto(null, List.of(section))), Map.of("dup", dup));

            AnchorIndex index = AnchorIndexBuilder.fromDto(book);

            assertThat(index.size()).isEqualTo(1);
            assertThat(index.find("dup")).get()
                    .extracting(AnchorInfo::elementType).isEqualTo("binary");
        }

        @Test
        @DisplayName("section with null/blank id is not registered")
        void skipsBlankSectionId() {
            Cite cite = new Cite("c1", List.of(), null);
            Section section = new Section(null, List.of(),
                    List.of(cite), List.of(), Map.of());
            FictionBookDto book = new FictionBookDto(null,
                    List.of(new BodyDto(null, List.of(section))), Map.of());

            AnchorIndex index = AnchorIndexBuilder.fromDto(book);

            // Зарегистрирован только cite, секция без id — нет.
            assertThat(index.getAllIds()).containsExactly("c1");
        }
    }
}
