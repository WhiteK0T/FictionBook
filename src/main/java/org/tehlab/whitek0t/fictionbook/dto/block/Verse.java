package org.tehlab.whitek0t.fictionbook.dto.block;

import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;

import java.util.List;

public record Verse(List<InlineElement> elements) implements BlockElement {
    public Verse {
        elements = List.copyOf(elements);
    }
}
