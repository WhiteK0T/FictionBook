package org.tehlab.whitek0t.fictionbook.dto.block;

import java.util.List;
import java.util.Map;

/**
 * Секция — FB2-элемент {@code <section>}, основная структурная единица тела книги
 * (глава, часть, сноска). Сама является {@link BlockElement} и может рекурсивно
 * содержать вложенные секции.
 *
 * @param id          якорь секции для ссылок/сносок; может быть {@code null}
 * @param title       заголовок секции ({@code <title>}); может быть сложным (несколько блоков)
 * @param content     содержимое: параграфы, стихи, цитаты, таблицы и т.п.
 * @param subSections вложенные подсекции (рекурсивная структура)
 * @param metadata    зарезервировано под CSS-классы, lang и пр. (задел под FB3);
 *                    {@code null} нормализуется в пустую карту
 */
public record Section(
        String id,
        List<BlockElement> title,
        List<BlockElement> content,
        List<Section> subSections,
        Map<String, String> metadata
) implements BlockElement {
    public Section {
        title = List.copyOf(title);
        content = List.copyOf(content);
        subSections = List.copyOf(subSections);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
