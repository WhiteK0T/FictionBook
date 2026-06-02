package org.tehlab.whitek0t.fictionbook.dto.description;

import java.util.List;

public record DocumentInfo(
        List<Author> authors,
        String programUsed,
        String date,
        String srcUrl,
        String srcOcr,
        String id,
        String version,
        List<String> history
) {
}