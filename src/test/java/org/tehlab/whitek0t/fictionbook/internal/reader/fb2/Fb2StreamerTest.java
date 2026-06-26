package org.tehlab.whitek0t.fictionbook.internal.reader.fb2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tehlab.whitek0t.fictionbook.api.FictionBookStreamer;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.internal.anchor.AnchorIndex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты потокового {@link Fb2Streamer}: ленивое чтение description и секций,
 * eager-получение бинарников по требованию и построение индекса якорей.
 */
@DisplayName("Fb2Streamer Tests")
class Fb2StreamerTest {

    private static final Path SAMPLE =
            Path.of("src", "test", "resources", "books", "fb2", "sample.fb2");

    private FictionBookStreamer open() throws Exception {
        return FictionBookStreamer.open(SAMPLE);
    }

    @Nested
    @DisplayName("Описание книги")
    class DescriptionReading {

        @Test
        @DisplayName("readDescription возвращает метаданные")
        void readsDescription() throws Exception {
            try (FictionBookStreamer s = open()) {
                Description d = s.readDescription();
                assertThat(d).isNotNull();
                assertThat(d.titleInfo().bookTitle()).isEqualTo("Образцовый рассказ");
            }
        }

        @Test
        @DisplayName("повторный вызов отдаёт тот же кэш")
        void readDescriptionIdempotent() throws Exception {
            try (FictionBookStreamer s = open()) {
                Description first = s.readDescription();
                Description second = s.readDescription();
                assertThat(second).isSameAs(first);
            }
        }
    }

    @Nested
    @DisplayName("Потоковое чтение секций")
    class SectionStreaming {

        @Test
        @DisplayName("секции верхнего уровня отдаются по одной, затем null")
        void streamsTopLevelSections() throws Exception {
            try (FictionBookStreamer s = open()) {
                s.readDescription();

                List<String> ids = new ArrayList<>();
                Section sec;
                while ((sec = s.readNextSection()) != null) {
                    ids.add(sec.id());
                }
                assertThat(ids).containsExactly("ch1", "ch2");
                // После конца — стабильно null.
                assertThat(s.readNextSection()).isNull();
            }
        }

        @Test
        @DisplayName("вложенные секции остаются внутри родителя")
        void keepsNestedSections() throws Exception {
            try (FictionBookStreamer s = open()) {
                Section first = s.readNextSection();
                assertThat(first.id()).isEqualTo("ch1");
                assertThat(first.subSections()).hasSize(1);
                assertThat(first.subSections().getFirst().id()).isEqualTo("ch1-1");
            }
        }

        @Test
        @DisplayName("секции читаются и без предварительного readDescription")
        void streamsWithoutReadingDescriptionFirst() throws Exception {
            try (FictionBookStreamer s = open()) {
                Section first = s.readNextSection();
                assertThat(first).isNotNull();
                assertThat(first.id()).isEqualTo("ch1");
            }
        }
    }

    @Nested
    @DisplayName("Бинарные ресурсы")
    class Resources {

        @Test
        @DisplayName("getResource резолвит по id и по #id")
        void resolvesById() throws Exception {
            try (FictionBookStreamer s = open()) {
                Resource cover = s.getResource("cover.png");
                assertThat(cover).isNotNull();
                assertThat(cover.contentType()).isEqualTo("image/png");
                // Ведущий '#' допускается (как в href картинок).
                assertThat(s.getResource("#pic.png")).isNotNull();
            }
        }

        @Test
        @DisplayName("неизвестный id → null")
        void unknownIdReturnsNull() throws Exception {
            try (FictionBookStreamer s = open()) {
                assertThat(s.getResource("missing.png")).isNull();
                assertThat(s.getResource(null)).isNull();
            }
        }

        @Test
        @DisplayName("ресурсы доступны независимо от позиции стрима секций")
        void resourcesIndependentOfSectionCursor() throws Exception {
            try (FictionBookStreamer s = open()) {
                // Вычитываем все секции, затем просим картинку — она в конце файла.
                while (s.readNextSection() != null) {
                    // drain
                }
                assertThat(s.getResource("cover.png")).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Индекс якорей")
    class Anchors {

        @Test
        @DisplayName("buildAnchorIndex находит секционные якоря")
        void buildsAnchorIndex() throws Exception {
            try (FictionBookStreamer s = open()) {
                AnchorIndex index = s.buildAnchorIndex();
                assertThat(index.contains("ch2")).isTrue();
                assertThat(index.canResolve("#ch2")).isTrue();
            }
        }
    }
}
