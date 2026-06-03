package org.tehlab.whitek0t.fictionbook.dto.description;

import java.util.List;

/**
 * Сведения о FB2-файле — элемент {@code <document-info>}: кто и чем создал файл,
 * его идентификатор, версия и история правок.
 *
 * @param authors     создатели файла (оцифровщики/составители)
 * @param programUsed программа, которой создан файл ({@code <program-used>}); может быть {@code null}
 * @param date         дата создания файла ({@code <date>}); может быть {@code null}
 * @param srcUrl       URL источника ({@code <src-url>}); может быть {@code null}
 * @param srcOcr       автор исходного OCR/оцифровки ({@code <src-ocr>}); может быть {@code null}
 * @param id           уникальный идентификатор документа ({@code <id>})
 * @param version      версия файла ({@code <version>})
 * @param history      строки истории правок ({@code <history>}) как plain-текст
 */
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
