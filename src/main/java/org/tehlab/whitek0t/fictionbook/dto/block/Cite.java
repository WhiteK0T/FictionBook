package org.tehlab.whitek0t.fictionbook.dto.block;

import java.util.List;

public record Cite(
        String id,
        List<BlockElement> content,
        String author
) implements BlockElement {
    public Cite {
        content = List.copyOf(content);
    }
}