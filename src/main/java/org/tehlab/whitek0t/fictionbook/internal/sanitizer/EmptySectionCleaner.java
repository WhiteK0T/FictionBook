package org.tehlab.whitek0t.fictionbook.internal.sanitizer;

import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;

/**
 * Удаляет пустые секции (главы без содержимого).
 *
 * <p>Секция считается пустой, если:</p>
 * <ul>
 *   <li>Нет заголовка (или он пустой)</li>
 *   <li>Нет содержимого (content)</li>
 *   <li>Нет вложенных секций</li>
 * </ul>
 *
 * <p>Обычно такие секции возникают как артефакты конвертации из других форматов.</p>
 */
public class EmptySectionCleaner implements Sanitizer {

    @Override
    public FictionBookDto sanitize(FictionBookDto book) {
        return FictionBookDtoTransformer.transform(book)
                .onSection(this::cleanSection)
                .apply();
    }

    private Section cleanSection(Section section) {
        if (isEmpty(section)) {
            return null; // Трансформер удалит секцию
        }
        return section;
    }

    private boolean isEmpty(Section section) {
        boolean hasTitle = section.title() != null && !section.title().isEmpty();
        boolean hasContent = section.content() != null && !section.content().isEmpty();
        boolean hasSubSections = section.subSections() != null && !section.subSections().isEmpty();

        return !hasTitle && !hasContent && !hasSubSections;
    }
}
