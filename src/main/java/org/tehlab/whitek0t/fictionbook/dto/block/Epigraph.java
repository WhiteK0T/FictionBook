package org.tehlab.whitek0t.fictionbook.dto.block;

import java.util.List;

public record Epigraph(
        String id,
        List<BlockElement> content,
        String author
) implements BlockElement {
    public Epigraph {
        content = List.copyOf(content);
    }
}