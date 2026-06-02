package org.tehlab.whitek0t.fictionbook.internal.anchor;

import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.*;

/**
 * Утилита для построения {@link AnchorIndex} из {@link FictionBookDto}.
 *
 * <p>Используется, когда нужен быстрый поиск якорей:</p>
 * <ul>
 *   <li>В санитайзерах (проверка валидности ссылок)</li>
 *   <li>В рендерерах (для генерации HTML-якорей)</li>
 *   <li>В DOM-модели (для редактирования)</li>
 * </ul>
 *
 * <p>Пример:</p>
 * <pre>{@code
 * FictionBookDto book = FictionBookIO.read(file);
 * AnchorIndex index = AnchorIndexBuilder.fromDto(book);
 *
 * // Теперь можно быстро проверять ссылки
 * if (index.canResolve("#note1")) {
 *     // ...
 * }
 * }</pre>
 */
public final class AnchorIndexBuilder {

    private AnchorIndexBuilder() {
    }

    /**
     * Строит индекс из DTO книги.
     * Обходит всё дерево и регистрирует все элементы с атрибутом {@code id}.
     *
     * @param book DTO книги
     * @return готовый immutable индекс
     */
    public static AnchorIndex fromDto(FictionBookDto book) {
        if (book == null) {
            return AnchorIndex.empty();
        }

        var builder = new Builder();

        // Регистрируем бинарники (картинки)
        for (Resource resource : book.resources().values()) {
            builder.register(resource.id(), "binary");
        }

        // Обходим все body и sections
        for (BodyDto body : book.bodies()) {
            String bodyName = body.name();
            for (Section section : body.sections()) {
                traverseSection(section, bodyName, builder);
            }
        }

        return builder.build();
    }

    private static void traverseSection(Section section, String bodyName, Builder builder) {
        if (section.id() != null && !section.id().isBlank()) {
            builder.register(section.id(), "section", bodyName);
        }

        // Обходим заголовок
        if (section.title() != null) {
            for (BlockElement block : section.title()) {
                traverseBlock(block, bodyName, builder);
            }
        }

        // Обходим содержимое
        if (section.content() != null) {
            for (BlockElement block : section.content()) {
                traverseBlock(block, bodyName, builder);
            }
        }

        // Рекурсивно обходим вложенные секции
        if (section.subSections() != null) {
            for (Section sub : section.subSections()) {
                traverseSection(sub, bodyName, builder);
            }
        }
    }

    private static void traverseBlock(BlockElement block, String bodyName, Builder builder) {
        if (block instanceof Cite cite && cite.id() != null) {
            builder.register(cite.id(), "cite", bodyName);
        } else if (block instanceof Epigraph epigraph && epigraph.id() != null) {
            builder.register(epigraph.id(), "epigraph", bodyName);
        } else if (block instanceof Poem poem && poem.id() != null) {
            builder.register(poem.id(), "poem", bodyName);
        }
        // Добавляйте другие блочные элементы с id по необходимости
    }

    /**
     * Внутренний mutable builder.
     */
    private static class Builder {
        private final java.util.Map<String, AnchorInfo> anchors = new java.util.LinkedHashMap<>();

        void register(String id, String elementType) {
            register(id, elementType, null);
        }

        void register(String id, String elementType, String bodyName) {
            if (id == null || id.isBlank()) return;

            AnchorInfo info = new AnchorInfo(id, elementType, -1, -1, bodyName, null);

            if (anchors.containsKey(id)) {
                // Прощающий режим: оставляем первый
                return;
            }

            anchors.put(id, info);
        }

        AnchorIndex build() {
            return new AnchorIndex(java.util.Map.copyOf(anchors));
        }
    }
}
