package org.tehlab.whitek0t.fictionbook.dto.inline;

import java.util.List;

/**
 * Нижний индекс (подстрочный текст) — FB2-элемент {@code <sub>}.
 *
 * @param elements вложенное содержимое
 */
public record Sub(List<InlineElement> elements) implements InlineElement {
}
