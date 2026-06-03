package org.tehlab.whitek0t.fictionbook.dto.inline;

/**
 * Маркерный интерфейс для встроенных (inline) элементов — содержимого параграфов,
 * стихов и других текстовых блоков.
 *
 * <p>Реализуется записями этого пакета: {@link Text} (простой текст), форматирование
 * {@link Strong}/{@link Emphasis}/{@link Strikethrough}/{@link Sub}/{@link Sup},
 * а также {@link Link} (ссылки и сноски) и {@link ImageRef} (встроенные картинки).
 * Намеренно <b>не</b> {@code sealed}: иерархию можно расширять пользовательскими
 * inline-типами.</p>
 *
 * @see org.tehlab.whitek0t.fictionbook.dto.block.BlockElement
 */
public interface InlineElement {
}
