package org.tehlab.whitek0t.fictionbook.dto.inline;

/**
 * Встроенная картинка внутри текста — FB2-элемент {@code <image>} (inline-вариант).
 *
 * <p>{@code href} обычно ссылается на {@code <binary>} по якорю ({@code "#cover"});
 * сами байты лежат в {@code resources} книги под этим id (без ведущего {@code #}).</p>
 *
 * @param href ссылка на ресурс-картинку (как правило, {@code "#id"})
 * @param alt  альтернативный текст; может быть {@code null}
 * @see org.tehlab.whitek0t.fictionbook.dto.Resource
 */
public record ImageRef(
        String href,
        String alt
) implements InlineElement {
}
