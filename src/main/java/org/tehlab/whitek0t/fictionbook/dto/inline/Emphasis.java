package org.tehlab.whitek0t.fictionbook.dto.inline;

import java.util.List;

/**
 * Курсив (акцент) — FB2-элемент {@code <emphasis>}. Контейнер: может содержать
 * вложенный текст и другое inline-форматирование.
 *
 * @param elements вложенное содержимое
 */
public record Emphasis(List<InlineElement> elements) implements InlineElement {
}
