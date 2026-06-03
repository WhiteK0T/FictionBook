package org.tehlab.whitek0t.fictionbook.dto.block;

import java.util.List;

/**
 * Цитата — FB2-элемент {@code <cite>}. Блок цитируемого текста с необязательным
 * указанием автора ({@code <text-author>}).
 *
 * @param id      якорь для ссылок; может быть {@code null}
 * @param content содержимое цитаты (параграфы, стихи и т.п.)
 * @param author  автор цитаты как plain-текст; может быть {@code null}
 */
public record Cite(
        String id,
        List<BlockElement> content,
        String author
) implements BlockElement {
    /** Делает защитную неизменяемую копию {@code content}. */
    public Cite {
        content = List.copyOf(content);
    }
}
