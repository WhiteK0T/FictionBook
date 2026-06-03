package org.tehlab.whitek0t.fictionbook.dto.block;

import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;

import java.util.List;

/**
 * Параграф — FB2-элемент {@code <p>}. Базовый блок текста; содержит смешанное
 * inline-содержимое (текст и форматирование).
 *
 * @param elements inline-содержимое параграфа; копируется в неизменяемый список
 */
public record Paragraph(List<InlineElement> elements) implements BlockElement {
    /** Делает защитную неизменяемую копию {@code elements}. */
    public Paragraph {
        elements = List.copyOf(elements);
    }
}
