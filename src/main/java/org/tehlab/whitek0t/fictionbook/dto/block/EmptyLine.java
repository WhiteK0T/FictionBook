package org.tehlab.whitek0t.fictionbook.dto.block;

/**
 * Пустая строка — FB2-элемент {@code <empty-line>}. Вертикальный отбойник между
 * блоками; содержимого не имеет.
 */
public record EmptyLine() implements BlockElement {
}
