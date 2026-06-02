package org.tehlab.whitek0t.fictionbook.dto.inline;

import java.util.List;

public record Emphasis(List<InlineElement> elements) implements InlineElement {
}
