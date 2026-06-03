package org.tehlab.whitek0t.fictionbook.dto.inline;

/**
 * Простой текстовый фрагмент — листовой inline-элемент без форматирования.
 *
 * @param value текстовое содержимое (может быть пустым, но обычно не {@code null})
 */
public record Text(String value) implements InlineElement {
}
