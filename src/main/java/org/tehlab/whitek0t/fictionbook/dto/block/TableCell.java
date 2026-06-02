package org.tehlab.whitek0t.fictionbook.dto.block;

import java.util.List;

public record TableCell(List<BlockElement> content) {
}  // внутри всегда блоки!
