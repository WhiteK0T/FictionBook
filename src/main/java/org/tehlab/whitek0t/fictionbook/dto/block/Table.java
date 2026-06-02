package org.tehlab.whitek0t.fictionbook.dto.block;

import java.util.List;

public record Table(List<TableRow> rows) implements BlockElement {
}
