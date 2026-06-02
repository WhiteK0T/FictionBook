package org.tehlab.whitek0t.fictionbook.internal.parser.stax;

import org.tehlab.whitek0t.fictionbook.dto.block.Verse;
import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Строит {@link Verse} (строку стихотворения).
 * Работает аналогично {@link ParagraphBuilder}.
 */
public class VerseBuilder implements NodeBuilder {

    private final List<InlineElement> elements = new ArrayList<>();
    private final StringBuilder textBuffer = new StringBuilder();

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
        return new Verse(List.copyOf(elements));
    }

    private void flushText() {
        if (!textBuffer.isEmpty()) {
            elements.add(new Text(textBuffer.toString()));
            textBuffer.setLength(0);
        }
    }
}