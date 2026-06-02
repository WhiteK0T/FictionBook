package org.tehlab.whitek0t.fictionbook.internal.parser.stax;

import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Link;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Строит {@link Link} из атрибута {@code href} и содержимого.
 *
 * <p>Пример:</p>
 * <pre>{@code
 * <a l:href="#note1" type="note">[1]</a>
 * }</pre>
 */
public class LinkBuilder implements NodeBuilder {

    private final String href;
    private final String type;
    private final List<InlineElement> elements = new ArrayList<>();
    private final StringBuilder textBuffer = new StringBuilder();

    public LinkBuilder(String href, String type) {
        this.href = href;
        this.type = type;
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

        // Если ссылка пустая (нет текста), добавляем href как текст
        if (elements.isEmpty() && href != null) {
            elements.add(new Text(href));
        }

        return new Link(href, type, List.copyOf(elements));
    }

    private void flushText() {
        if (!textBuffer.isEmpty()) {
            elements.add(new Text(textBuffer.toString()));
            textBuffer.setLength(0);
        }
    }
}