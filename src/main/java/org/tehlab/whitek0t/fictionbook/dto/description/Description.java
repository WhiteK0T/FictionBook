package org.tehlab.whitek0t.fictionbook.dto.description;

/**
 * Метаданные книги — FB2-элемент {@code <description>}. Объединяет три блока сведений:
 * о произведении, о файле и об издании.
 *
 * @param titleInfo   сведения о произведении ({@code <title-info>}): авторы, название, жанры…
 * @param documentInfo сведения о FB2-файле ({@code <document-info>}): id, версия, история…
 * @param publishInfo  сведения об издании ({@code <publish-info>}); может быть {@code null}
 */
public record Description(
        TitleInfo titleInfo,
        DocumentInfo documentInfo,
        PublishInfo publishInfo
) {
}
