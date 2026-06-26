package org.tehlab.whitek0t.fictionbook.dto.block;

/**
 * Блочная картинка — FB2-элемент {@code <image>} как прямой потомок
 * {@code <section>}/{@code <cite>}/{@code <epigraph>} (иллюстрация между абзацами),
 * в отличие от инлайнового {@link org.tehlab.whitek0t.fictionbook.dto.inline.ImageRef}
 * внутри {@code <p>}.
 *
 * <p>{@code href} обычно ссылается на {@code <binary>} по якорю ({@code "#cover"});
 * сами байты лежат в {@code resources} книги под этим id (без ведущего {@code #}).
 * Поля повторяют inline-вариант, чтобы рендеринг и резолв ресурсов были единообразны.</p>
 *
 * @param href ссылка на ресурс-картинку (как правило, {@code "#id"})
 * @param alt  альтернативный текст; может быть {@code null}
 * @see org.tehlab.whitek0t.fictionbook.dto.inline.ImageRef
 * @see org.tehlab.whitek0t.fictionbook.dto.Resource
 */
public record BlockImage(
        String href,
        String alt
) implements BlockElement {
}
