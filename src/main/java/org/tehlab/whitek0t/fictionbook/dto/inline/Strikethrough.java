package org.tehlab.whitek0t.fictionbook.dto.inline;

import java.util.List;

/**
 * Зачёркнутый текст — FB2-элемент {@code <strikethrough>}. Контейнер: может
 * содержать вложенный текст и другое inline-форматирование.
 *
 * @param elements вложенное содержимое
 */
public record Strikethrough(List<InlineElement> elements) implements InlineElement {
}
