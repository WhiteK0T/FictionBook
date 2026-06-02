package org.tehlab.whitek0t.fictionbook.internal.parser.jackson;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

/**
 * Аннотация книги. Содержит mixed content (параграфы с форматированием).
 * Читается как сырой XML-текст, затем парсится через Fb2BlockParser.
 */

public class Fb2AnnotationJax {

    /**
     * Сырой XML-текст внутри {@code <annotation>...</annotation>}.
     * Пример: "<p>Текст</p><p>Ещё <strong>текст</strong></p>"
     */
    @JacksonXmlText
    public String rawXml;
}
