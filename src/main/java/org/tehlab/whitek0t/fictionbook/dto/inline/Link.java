package org.tehlab.whitek0t.fictionbook.dto.inline;

import java.util.List;

/**
 * Ссылка или сноска — FB2-элемент {@code <a>} (атрибут {@code l:href} из пространства
 * имён xlink).
 *
 * <p>Цель ({@code href}) может быть внутренним якорем ({@code "#n1"}), внешним
 * URL ({@code "http://…"}) или ссылкой в другой файл ({@code "other.fb2#id"}).
 * Сноски помечаются {@code type="note"} и обычно ведут на секцию в теле
 * {@code <body name="notes">}.</p>
 *
 * @param href     цель ссылки (якорь, URL или внешняя ссылка)
 * @param type     тип ссылки; {@code "note"} для сносок, иначе обычно {@code null}
 * @param elements видимое содержимое ссылки (текст/форматирование)
 */
public record Link(
        String href,
        String type,
        List<InlineElement> elements
) implements InlineElement {
}
