package org.tehlab.whitek0t.fictionbook.dto.block;

import java.util.List;

/**
 * Строка таблицы — FB2-элемент {@code <tr>}. Набор ячеек ({@link TableCell}).
 *
 * @param cells ячейки строки
 * @see Table
 */
public record TableRow(List<TableCell> cells) {
}
