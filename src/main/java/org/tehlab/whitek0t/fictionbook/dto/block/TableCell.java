package org.tehlab.whitek0t.fictionbook.dto.block;

import java.util.List;

/**
 * Ячейка таблицы — FB2-элементы {@code <td>}/{@code <th>}. Содержимое ячейки —
 * всегда блочные элементы.
 *
 * @param content блочное содержимое ячейки
 * @see TableRow
 */
public record TableCell(List<BlockElement> content) {
}
