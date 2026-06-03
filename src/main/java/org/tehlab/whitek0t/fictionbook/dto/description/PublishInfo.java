package org.tehlab.whitek0t.fictionbook.dto.description;

/**
 * Сведения об издании — FB2-элемент {@code <publish-info>}: данные бумажного
 * (или иного) издания-источника.
 *
 * @param bookName  название издания ({@code <book-name>}); может быть {@code null}
 * @param publisher издательство ({@code <publisher>}); может быть {@code null}
 * @param city       город издания ({@code <city>}); может быть {@code null}
 * @param year       год издания ({@code <year>}); может быть {@code null}
 * @param isbn       ISBN издания ({@code <isbn>}); может быть {@code null}
 */
public record PublishInfo(
        String bookName,
        String publisher,
        String city,
        String year,
        String isbn
) {
}
