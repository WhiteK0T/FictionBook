package org.tehlab.whitek0t.fictionbook.dto.inline;

import java.util.List;

/**
 * Верхний индекс (надстрочный текст) — FB2-элемент {@code <sup>}.
 *
 * @param elements вложенное содержимое
 */
public record Sup(List<InlineElement> elements) implements InlineElement {
}
