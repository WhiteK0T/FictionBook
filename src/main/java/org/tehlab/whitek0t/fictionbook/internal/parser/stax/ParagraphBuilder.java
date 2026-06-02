package org.tehlab.whitek0t.fictionbook.internal.parser.stax;

import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Строит {@link Paragraph} из текста и инлайн-элементов.
 *
 * <p>Пример входного XML:</p>
 * <pre>{@code
 * <p>Обычный текст <strong>жирный</strong> и <emphasis>курсив</emphasis>.</p>
 * }</pre>
 *
 * <p>Результат:</p>
 * <pre>{@code
 * Paragraph([
 *   Text("Обычный текст "),
 *   Strong([Text("жирный")]),
 *   Text(" и "),
 *   Emphasis([Text("курсив")]),
 *   Text(".")
 * ])
 * }</pre>
 */
public class ParagraphBuilder implements NodeBuilder {

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
        // Игнорируем не-инлайн элементы (прощающий режим)
    }

    @Override
    public Object build() {
        flushText();
        return new Paragraph(List.copyOf(elements));
    }

    /**
     * Сбрасывает накопленный текст в Text-ноду.
     * Вызывается перед добавлением дочернего элемента и при build().
     */
    private void flushText() {
        if (!textBuffer.isEmpty()) {
            String text = textBuffer.toString();
            // Не добавляем пустые Text-ноды (только пробелы между тегами игнорируем? нет, сохраняем)
            elements.add(new Text(text));
            textBuffer.setLength(0);
        }
    }
}