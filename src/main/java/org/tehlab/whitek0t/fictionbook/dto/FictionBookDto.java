package org.tehlab.whitek0t.fictionbook.dto;


import org.tehlab.whitek0t.fictionbook.dto.description.Description;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Корневой неизменяемый DTO книги — результат разбора FB2/FB3 и центральный объект
 * библиотеки. Готов к рендерингу, сериализации или передаче в UI.
 *
 * <p>Преобразование дерева (санитайзеры, правки) выполняется пересозданием записей,
 * а не мутацией. Сам DTO не содержит индексов: поиск по якорям строится отдельно
 * через {@code AnchorIndexBuilder.fromDto()}.</p>
 *
 * @param description метаданные книги ({@code <description>})
 * @param bodies      тела книги ({@code <body>}): основное и, опционально, сноски
 *                    ({@code name="notes"}); копируются в неизменяемый список
 * @param resources   бинарные ресурсы ({@code <binary>}) по их id; порядок документа
 *                    сохраняется ({@link LinkedHashMap}), что важно для стабильного
 *                    round-trip при перезаписи
 */
public record FictionBookDto(
        Description description,
        List<BodyDto> bodies,
        Map<String, Resource> resources
) {
    /** Копирует {@code bodies} и сохраняет порядок документа для {@code resources}. */
    public FictionBookDto {
        bodies = List.copyOf(bodies);
        // Сохраняем порядок документа: Map.copyOf даёт неопределённый (зависящий от
        // соли и раскладки хэшей) порядок итерации, из-за чего <binary> при перезаписи
        // переставлялись и ломался round-trip фикспоинт.
        resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources));
    }
}
