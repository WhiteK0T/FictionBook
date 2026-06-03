package org.tehlab.whitek0t.fictionbook.dto.block;

/**
 * Маркерный интерфейс для всех блочных элементов тела книги — параграфов, секций,
 * стихов, цитат, таблиц и т.п.
 *
 * <p>Реализуется записями ({@code record}) из этого пакета: {@link Paragraph},
 * {@link Section}, {@link Poem}, {@link Cite}, {@link Epigraph}, {@link Table},
 * {@link EmptyLine}. Намеренно <b>не</b> {@code sealed}: библиотека позволяет
 * расширять иерархию пользовательскими блочными типами.</p>
 *
 * @see org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement
 */
public interface BlockElement {
}
