package org.tehlab.whitek0t.fictionbook.dto.inline;

import java.util.List;

public record Link(
        String href,                       // "#n1" или "http://..." или "other.fb2#id"
        String type,                       // "note" для сносок
        List<InlineElement> elements
) implements InlineElement {
}