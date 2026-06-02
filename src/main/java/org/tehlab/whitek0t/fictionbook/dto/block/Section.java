package org.tehlab.whitek0t.fictionbook.dto.block;

import java.util.List;
import java.util.Map;

public record Section(
        String id,                          // якорь для ссылок
        List<BlockElement> title,           // заголовок (может быть сложным)
        List<BlockElement> content,         // параграфы, стихи, таблицы
        List<Section> subSections,           // рекурсивная вложенность
        Map<String, String> metadata   // 🆕 зарезервировано под CSS-классы, lang, etc.
) implements BlockElement {
    public Section {
        title = List.copyOf(title);
        content = List.copyOf(content);
        subSections = List.copyOf(subSections);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
