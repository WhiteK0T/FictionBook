package org.tehlab.whitek0t.fictionbook.internal.sanitizer;

import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;

/**
 * Удаляет пустые параграфы.
 *
 * <p>Параграф считается пустым, если:</p>
 * <ul>
 *   <li>Список элементов пуст</li>
 *   <li>Содержит только Text-ноды с пустыми или пробельными строками</li>
 * </ul>
 */
public class EmptyParagraphCleaner implements Sanitizer {

    @Override
    public FictionBookDto sanitize(FictionBookDto book) {
        return FictionBookDtoTransformer.transform(book)
                .onParagraph(this::cleanParagraph)
                .apply();
    }

    private Paragraph cleanParagraph(Paragraph p) {
        if (isEmpty(p)) {
            // Возвращаем null — трансформер удалит этот параграф
            return null;
        }
        return p;
    }

    private boolean isEmpty(Paragraph p) {
        if (p.elements().isEmpty()) {
            return true;
        }

        for (InlineElement element : p.elements()) {
            if (!(element instanceof Text)) {
                return false; // Есть не-текстовый элемент (ссылка, картинка)
            }
            Text text = (Text) element;
            if (text.value() != null && !text.value().isBlank()) {
                return false; // Есть непустой текст
            }
        }

        return true; // Все элементы — пустые Text
    }
}
