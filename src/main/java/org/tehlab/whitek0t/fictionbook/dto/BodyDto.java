package org.tehlab.whitek0t.fictionbook.dto;

import org.tehlab.whitek0t.fictionbook.dto.block.Section;

import java.util.List;

/**
 * Тело книги — FB2-элемент {@code <body>}. Книга обычно имеет основное тело и,
 * опционально, отдельное тело сносок ({@code <body name="notes">}).
 *
 * @param name     имя тела: {@code null} для основного, {@code "notes"} для сносок
 * @param sections секции верхнего уровня; копируются в неизменяемый список
 */
public record BodyDto(
        String name,
        List<Section> sections
) {
    public BodyDto {
        sections = List.copyOf(sections);
    }
}
