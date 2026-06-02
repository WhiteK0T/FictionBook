package org.tehlab.whitek0t.fictionbook.internal.parser.stax;

import org.tehlab.whitek0t.fictionbook.dto.inline.ImageRef;

/**
 * Строит {@link ImageRef} из атрибутов {@code <image>}.
 *
 * <p>Пример:</p>
 * <pre>{@code
 * <image l:href="#cover" alt="Обложка книги"/>
 * }</pre>
 *
 * <p>Это пустой тег (self-closing), поэтому {@link #appendText} и {@link #addChild}
 * не используются.</p>
 */
public class ImageBuilder implements NodeBuilder {

    private final String href;
    private final String alt;

    public ImageBuilder(String href, String alt) {
        this.href = href;
        this.alt = alt;
    }

    @Override
    public void appendText(String text) {
        // Игнорируем (image — пустой тег)
    }

    @Override
    public void addChild(Object childNode) {
        // Игнорируем (image не имеет дочерних элементов)
    }

    @Override
    public Object build() {
        return new ImageRef(href, alt);
    }
}