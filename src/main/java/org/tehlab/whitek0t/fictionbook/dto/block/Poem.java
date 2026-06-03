package org.tehlab.whitek0t.fictionbook.dto.block;

import java.util.List;

/**
 * Стихотворение — FB2-элемент {@code <poem>}. Состоит из строф ({@link Stanza}),
 * с необязательными заголовком, эпиграфом и автором.
 *
 * @param id       якорь для ссылок; может быть {@code null}
 * @param title    заголовок стихотворения ({@code <title>}); может быть пустым
 * @param stanzas  строфы стихотворения
 * @param epigraph эпиграф к стихотворению; может быть пустым
 * @param author   автор как plain-текст ({@code <text-author>}); может быть {@code null}
 */
public record Poem(
        String id,
        List<BlockElement> title,
        List<Stanza> stanzas,
        List<BlockElement> epigraph,
        String author
) implements BlockElement {
}
