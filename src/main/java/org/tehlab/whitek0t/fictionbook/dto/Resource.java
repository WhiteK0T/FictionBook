package org.tehlab.whitek0t.fictionbook.dto;

/**
 * DTO ресурса (картинки).
 * Содержит метаданные + ленивый провайдер данных, чтобы не грузить
 * все бинарники в память сразу.
 */
public record Resource(
        String id,
        String contentType,
        ResourceDataProvider dataProvider
) {
}

