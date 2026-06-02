package org.tehlab.whitek0t.fictionbook.dto.inline;

public record ImageRef(
        String href,                       // "#cover"
        String alt
) implements InlineElement {
}
