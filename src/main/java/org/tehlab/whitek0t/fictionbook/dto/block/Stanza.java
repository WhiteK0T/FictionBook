package org.tehlab.whitek0t.fictionbook.dto.block;

import java.util.List;

public record Stanza(List<Verse> verses) implements BlockElement {
}
