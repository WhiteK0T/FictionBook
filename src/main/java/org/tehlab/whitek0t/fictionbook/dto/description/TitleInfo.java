package org.tehlab.whitek0t.fictionbook.dto.description;

import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;

import java.util.List;

public record TitleInfo(
        List<Author> authors,
        List<String> genres,
        String bookTitle,
        List<BlockElement> annotation,
        String lang,
        String srcLang,
        Sequence sequence,
        List<String> coverImageIds        // ID обложек из resources
) {
}
