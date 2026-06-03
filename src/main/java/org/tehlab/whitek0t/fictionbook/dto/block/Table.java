package org.tehlab.whitek0t.fictionbook.dto.block;

import java.util.List;

/**
 * Таблица — FB2-элемент {@code <table>}. Набор строк ({@link TableRow}).
 *
 * @param rows строки таблицы
 */
public record Table(List<TableRow> rows) implements BlockElement {
}
