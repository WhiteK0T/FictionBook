package org.tehlab.whitek0t.fictionbook.internal.writer.fb3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tehlab.whitek0t.fictionbook.api.FictionBookFormat;
import org.tehlab.whitek0t.fictionbook.api.FictionBookIO;
import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.ResourceDataProvider;
import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;
import org.tehlab.whitek0t.fictionbook.dto.block.Cite;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.description.Author;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.dto.description.DocumentInfo;
import org.tehlab.whitek0t.fictionbook.dto.description.Sequence;
import org.tehlab.whitek0t.fictionbook.dto.description.TitleInfo;
import org.tehlab.whitek0t.fictionbook.dto.inline.ImageRef;
import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Strong;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;
import org.tehlab.whitek0t.fictionbook.internal.reader.fb3.Fb3Reader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты {@link Fb3Writer}: запись {@link FictionBookDto} в OPC/ZIP-контейнер и
 * round-trip через {@link Fb3Reader} (write → read → сравнение ключевых полей).
 */
@DisplayName("Fb3Writer Tests")
class Fb3WriterTest {

    /** Собирает книгу с описанием, секцией (strong + картинка), цитатой и ресурсом. */
    private FictionBookDto sampleBook() {
        TitleInfo titleInfo = new TitleInfo(
                List.of(new Author("Лев", "Николаевич", "Толстой")),
                List.of("prose_classic"),
                "Война и мир",
                List.of(new Paragraph(List.of(new Text("Аннотация.")))),
                "ru",
                null,
                new Sequence("Классика", 3),
                List.of("cover.png")
        );
        DocumentInfo documentInfo = new DocumentInfo(
                List.of(), null, null, null, null,
                "11111111-2222-3333-4444-555555555555", "1.0", List.of()
        );
        Description description = new Description(titleInfo, documentInfo, null);

        Paragraph p1 = new Paragraph(List.of(
                new Text("Текст с "),
                new Strong(List.of(new Text("жирным"))),
                new Text(".")
        ));
        Paragraph p2 = new Paragraph(List.of(new ImageRef("#pic.png", "рисунок")));
        Cite cite = new Cite(null, List.of(new Paragraph(List.of(new Text("Цитата.")))), "Автор");

        Section sub = new Section("sec-1-1", List.of(), List.of(
                new Paragraph(List.of(new Text("Вложенный.")))), List.of(), Map.of());
        Section sec = new Section("sec-1",
                List.of(new Paragraph(List.of(new Text("Глава первая")))),
                List.of(p1, p2, cite),
                List.of(sub),
                Map.of());

        BodyDto body = new BodyDto(null, List.of(sec));

        ResourceDataProvider provider = () -> new ByteArrayInputStream(
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});
        Resource cover = new Resource("cover.png", "image/png", provider);
        Resource pic = new Resource("pic.png", "image/png", provider);

        return new FictionBookDto(description, List.of(body),
                new java.util.LinkedHashMap<>(Map.of("cover.png", cover, "pic.png", pic)));
    }

    private FictionBookDto roundTrip(FictionBookDto book) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Fb3Writer().write(book, baos);
        return new Fb3Reader().read(new ByteArrayInputStream(baos.toByteArray()));
    }

    @Nested
    @DisplayName("Round-trip")
    class RoundTrip {

        @Test
        @DisplayName("метаданные сохраняются")
        void preservesMetadata() throws Exception {
            var d = roundTrip(sampleBook()).description();
            assertThat(d.titleInfo().bookTitle()).isEqualTo("Война и мир");
            assertThat(d.titleInfo().lang()).isEqualTo("ru");
            assertThat(d.titleInfo().genres()).containsExactly("prose_classic");
            assertThat(d.titleInfo().sequence().name()).isEqualTo("Классика");
            assertThat(d.titleInfo().sequence().number()).isEqualTo(3);
            assertThat(d.documentInfo().id()).isEqualTo("11111111-2222-3333-4444-555555555555");
            assertThat(d.documentInfo().version()).isEqualTo("1.0");

            Author a = d.titleInfo().authors().get(0);
            assertThat(a.firstName()).isEqualTo("Лев");
            assertThat(a.lastName()).isEqualTo("Толстой");
        }

        @Test
        @DisplayName("структура тела и вложенность сохраняются")
        void preservesBodyStructure() throws Exception {
            Section sec = roundTrip(sampleBook()).bodies().get(0).sections().get(0);
            assertThat(sec.id()).isEqualTo("sec-1");
            assertThat(sec.subSections()).hasSize(1);
            assertThat(sec.subSections().get(0).id()).isEqualTo("sec-1-1");

            boolean hasStrong = ((Paragraph) sec.content().get(0)).elements().stream()
                    .anyMatch(e -> e instanceof Strong);
            assertThat(hasStrong).isTrue();
            assertThat(sec.content()).anyMatch(b -> b instanceof Cite);
        }

        @Test
        @DisplayName("картинки и ссылки сохраняются")
        void preservesImages() throws Exception {
            FictionBookDto out = roundTrip(sampleBook());
            assertThat(out.resources()).containsKeys("cover.png", "pic.png");

            Section sec = out.bodies().get(0).sections().get(0);
            ImageRef img = firstImage(sec.content().get(1));
            assertThat(img).isNotNull();
            assertThat(img.href()).isEqualTo("#pic.png");
            assertThat(img.alt()).isEqualTo("рисунок");
        }

        @Test
        @DisplayName("обложка сохраняется в coverImageIds")
        void preservesCover() throws Exception {
            assertThat(roundTrip(sampleBook()).description().titleInfo().coverImageIds())
                    .containsExactly("cover.png");
        }

        private ImageRef firstImage(BlockElement block) {
            if (block instanceof Paragraph p) {
                for (InlineElement e : p.elements()) {
                    if (e instanceof ImageRef img) return img;
                }
            }
            return null;
        }
    }

    @Nested
    @DisplayName("Container & facade")
    class Container {

        @Test
        @DisplayName("записанный файл — валидный ZIP/FB3 (magic bytes)")
        void writesZipContainer(@TempDir Path dir) throws Exception {
            Path file = dir.resolve("out.fb3");
            FictionBookIO.write(sampleBook(), file);

            byte[] head = Files.readAllBytes(file);
            assertThat(head[0]).isEqualTo((byte) 0x50); // 'P'
            assertThat(head[1]).isEqualTo((byte) 0x4B); // 'K'
            assertThat(FictionBookFormat.detect(file)).isEqualTo(FictionBookFormat.FB3);
        }

        @Test
        @DisplayName("FictionBookIO write→read через файл")
        void facadeWriteThenRead(@TempDir Path dir) throws Exception {
            Path file = dir.resolve("book.fb3");
            FictionBookIO.write(sampleBook(), file);
            FictionBookDto out = FictionBookIO.read(file);
            assertThat(out.description().titleInfo().bookTitle()).isEqualTo("Война и мир");
            assertThat(out.bodies().get(0).sections()).hasSize(1);
        }
    }
}
