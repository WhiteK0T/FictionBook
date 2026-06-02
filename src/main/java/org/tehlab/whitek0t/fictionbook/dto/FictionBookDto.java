package org.tehlab.whitek0t.fictionbook.dto;


import org.tehlab.whitek0t.fictionbook.dto.description.Description;

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
        resources = Map.copyOf(resources);
    }
}
