package org.tehlab.whitek0t.fictionbook.dto;

import org.tehlab.whitek0t.fictionbook.dto.block.Section;

import java.util.List;

public record BodyDto(
        String name,               // null для основного тела, "notes" для примечаний
        List<Section> sections
) {
    public BodyDto {
        sections = List.copyOf(sections);
    }
}
