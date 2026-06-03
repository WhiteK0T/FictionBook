package org.tehlab.whitek0t.fictionbook.dto.inline;

import java.util.List;

/**
 * Полужирный текст — FB2-элемент {@code <strong>}. Контейнер: может содержать
 * вложенный текст и другое inline-форматирование.
 *
 * @param elements вложенное содержимое
 */
public record Strong(List<InlineElement> elements) implements InlineElement {
}
