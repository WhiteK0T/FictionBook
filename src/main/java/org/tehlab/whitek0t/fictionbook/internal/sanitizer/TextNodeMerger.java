package org.tehlab.whitek0t.fictionbook.internal.sanitizer;

import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.inline.Emphasis;
import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Link;
import org.tehlab.whitek0t.fictionbook.dto.inline.Strikethrough;
import org.tehlab.whitek0t.fictionbook.dto.inline.Strong;
import org.tehlab.whitek0t.fictionbook.dto.inline.Sub;
import org.tehlab.whitek0t.fictionbook.dto.inline.Sup;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Склейвает соседние Text-ноды в одну.
 *
 * <p>При парсинге StAX часто разбивает текст на несколько кусков
 * (из-за CDATA, лимитов буфера, escaped entities). Этот санитайзер
 * приводит текст к каноническому виду — одна Text-нода подряд.</p>
 *
 * <p>Пример:</p>
 * <pre>{@code
 * [Text("Hello "), Text("world")] → [Text("Hello world")]
 * }</pre>
 */
public class TextNodeMerger implements Sanitizer {

    @Override
    public FictionBookDto sanitize(FictionBookDto book) {
        return FictionBookDtoTransformer.transform(book)
                .onParagraph(this::mergeTextNodes)
                .apply();
    }

    private Paragraph mergeTextNodes(Paragraph p) {
        List<InlineElement> merged = mergeInlineList(p.elements());

        // Если ничего не изменилось — возвращаем оригинал (экономия памяти)
        if (merged.equals(p.elements())) {
            return p;
        }

        return new Paragraph(List.copyOf(merged));
    }

    private List<InlineElement> mergeInlineList(List<InlineElement> elements) {
        if (elements.isEmpty()) return elements;

        List<InlineElement> result = new ArrayList<>();
        StringBuilder textBuffer = new StringBuilder();

        for (InlineElement element : elements) {
            if (element instanceof Text t) {
                // Накапливаем текст
                if (t.value() != null) {
                    textBuffer.append(t.value());
                }
            } else {
                // Сбрасываем накопленный текст перед не-текстовым элементом
                flushTextBuffer(result, textBuffer);
                // Рекурсивно склеиваем текст и внутри контейнерных инлайнов:
                // читатель может разбить текст на куски на любом уровне вложенности.
                result.add(mergeInside(element));
            }
        }

        // Сбрасываем остаток
        flushTextBuffer(result, textBuffer);

        return result;
    }

    /** Контейнерные инлайны перестраиваем со склейкой их детей; листья — как есть. */
    private InlineElement mergeInside(InlineElement element) {
        if (element instanceof Strong s) {
            return new Strong(mergeInlineList(s.elements()));
        } else if (element instanceof Emphasis e) {
            return new Emphasis(mergeInlineList(e.elements()));
        } else if (element instanceof Strikethrough s) {
            return new Strikethrough(mergeInlineList(s.elements()));
        } else if (element instanceof Sub s) {
            return new Sub(mergeInlineList(s.elements()));
        } else if (element instanceof Sup s) {
            return new Sup(mergeInlineList(s.elements()));
        } else if (element instanceof Link l) {
            return new Link(l.href(), l.type(), mergeInlineList(l.elements()));
        }
        return element;
    }

    private void flushTextBuffer(List<InlineElement> result, StringBuilder buffer) {
        if (!buffer.isEmpty()) {
            result.add(new Text(buffer.toString()));
            buffer.setLength(0);
        }
    }
}
