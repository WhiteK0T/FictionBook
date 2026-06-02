package org.tehlab.whitek0t.fictionbook.internal.parser.stax;

import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Универсальный строитель для инлайн-контейнеров: {@code <strong>}, {@code <emphasis>},
 * {@code <strikethrough>}, {@code <sub>}, {@code <sup>}.
 *
 * <p>Использует {@link Function} для создания конкретного DTO-типа:</p>
 * <pre>{@code
 * new InlineContainerBuilder<>(Strong::new)
 * new InlineContainerBuilder<>(Emphasis::new)
 * }</pre>
 *
 * @param <T> тип DTO (Strong, Emphasis, etc.)
 */
public class InlineContainerBuilder<T> implements NodeBuilder {

    private final List<InlineElement> elements = new ArrayList<>();
    private final StringBuilder textBuffer = new StringBuilder();
    private final Function<List<InlineElement>, T> factory;

    public InlineContainerBuilder(Function<List<InlineElement>, T> factory) {
        this.factory = factory;
    }

    @Override
    public void appendText(String text) {
        if (text != null) {
            textBuffer.append(text);
        }
    }

    @Override
    public void addChild(Object childNode) {
        flushText();
        if (childNode instanceof InlineElement inline) {
            elements.add(inline);
        }
    }

    @Override
    public Object build() {
        flushText();
        return factory.apply(List.copyOf(elements));
    }

    private void flushText() {
        if (!textBuffer.isEmpty()) {
            elements.add(new Text(textBuffer.toString()));
            textBuffer.setLength(0);
        }
    }
}