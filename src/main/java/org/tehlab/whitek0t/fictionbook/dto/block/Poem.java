package org.tehlab.whitek0t.fictionbook.dto.block;

import java.util.List;

// --- Стихи ---
public record Poem(
        String id,
        List<BlockElement> title,
        List<Stanza> stanzas,
        List<BlockElement> epigraph,
        String author
) implements BlockElement {
}
