package org.tehlab.whitek0t.fictionbook.dto.block;

import java.util.List;

/**
 * Эпиграф — FB2-элемент {@code <epigraph>}. Блок в начале книги или секции с
 * необязательным указанием автора ({@code <text-author>}).
 *
 * @param id      якорь для ссылок; может быть {@code null}
 * @param content содержимое эпиграфа (параграфы, стихи и т.п.)
 * @param author  автор эпиграфа как plain-текст; может быть {@code null}
 */
public record Epigraph(
        String id,
        List<BlockElement> content,
        String author
) implements BlockElement {
    public Epigraph {
        content = List.copyOf(content);
    }
}
