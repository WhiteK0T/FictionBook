package org.tehlab.whitek0t.fictionbook.dto;

/**
 * Бинарный ресурс книги (обычно картинка) — FB2-элемент {@code <binary>}.
 * Хранит метаданные и провайдер данных; на доступ к самим байтам идут через
 * {@link ResourceDataProvider}, чтобы не держать все бинарники в памяти разом.
 *
 * @param id           идентификатор ресурса, по которому на него ссылаются (без {@code #})
 * @param contentType  MIME-тип содержимого (напр. {@code "image/jpeg"})
 * @param dataProvider поставщик потока с байтами ресурса
 * @see org.tehlab.whitek0t.fictionbook.dto.inline.ImageRef
 */
public record Resource(
        String id,
        String contentType,
        ResourceDataProvider dataProvider
) {
}
