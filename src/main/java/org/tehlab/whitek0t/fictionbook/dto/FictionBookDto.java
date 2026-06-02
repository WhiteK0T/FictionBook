package org.tehlab.whitek0t.fictionbook.dto;


import org.tehlab.whitek0t.fictionbook.dto.description.Description;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Корневой immutable DTO книги.
 * Результат парсинга FB2/FB3, готов к сериализации в JSON или передаче в UI.
 */
public record FictionBookDto(
        Description description,
        List<BodyDto> bodies,
        Map<String, Resource> resources   // id -> Resource
) {
    public FictionBookDto {
        bodies = List.copyOf(bodies);
        // Сохраняем порядок документа: Map.copyOf даёт неопределённый (зависящий от
        // соли и раскладки хэшей) порядок итерации, из-за чего <binary> при перезаписи
        // переставлялись и ломался round-trip фикспоинт.
        resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources));
    }
}
