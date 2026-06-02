package org.tehlab.whitek0t.fictionbook.dto.block;

import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;

import java.util.List;

public record Paragraph(List<InlineElement> elements) implements BlockElement {
    public Paragraph {
        elements = List.copyOf(elements);
    }
}
