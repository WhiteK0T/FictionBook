package org.tehlab.whitek0t.fictionbook.dto.block;

import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;

import java.util.List;

/**
 * Стихотворная строка — FB2-элемент {@code <v>} внутри строфы. Содержит смешанное
 * inline-содержимое.
 *
 * @param elements inline-содержимое строки; копируется в неизменяемый список
 * @see Stanza
 */
public record Verse(List<InlineElement> elements) implements BlockElement {
    public Verse {
        elements = List.copyOf(elements);
    }
}
