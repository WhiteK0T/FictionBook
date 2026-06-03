package org.tehlab.whitek0t.fictionbook.dto.description;

import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;

import java.util.List;

/**
 * Сведения о произведении — FB2-элемент {@code <title-info>}: авторы, название,
 * жанры, аннотация, язык, серия и обложки.
 *
 * @param authors       авторы книги
 * @param genres         жанровые коды FB2 (напр. {@code "prose_classic"})
 * @param bookTitle      название книги ({@code <book-title>})
 * @param annotation     аннотация как блочное содержимое ({@code <annotation>}); может быть пустой
 * @param lang           язык произведения ({@code <lang>}, напр. {@code "ru"})
 * @param srcLang        язык оригинала для переводов ({@code <src-lang>}); может быть {@code null}
 * @param sequence       серия/цикл ({@code <sequence>}); может быть {@code null}
 * @param coverImageIds  id обложек из {@code resources} книги (без ведущего {@code #})
 */
public record TitleInfo(
        List<Author> authors,
        List<String> genres,
        String bookTitle,
        List<BlockElement> annotation,
        String lang,
        String srcLang,
        Sequence sequence,
        List<String> coverImageIds
) {
}
