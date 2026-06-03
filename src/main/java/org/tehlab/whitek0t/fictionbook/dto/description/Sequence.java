package org.tehlab.whitek0t.fictionbook.dto.description;

/**
 * Серия (цикл) книги — FB2-элемент {@code <sequence>}: название серии и порядковый
 * номер книги в ней.
 *
 * @param name   название серии (атрибут {@code name})
 * @param number номер книги в серии (атрибут {@code number}); может быть {@code null}
 */
public record Sequence(String name, Integer number) {
}
