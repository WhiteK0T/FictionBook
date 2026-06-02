package org.tehlab.whitek0t.fictionbook.dto.description;

public record PublishInfo(
        String bookName,
        String publisher,
        String city,
        String year,
        String isbn
) {
}