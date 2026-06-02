package org.tehlab.whitek0t.fictionbook.internal.parser.jackson;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

/**
 * История документа. Содержит mixed content (параграфы с форматированием).
 * Читается как сырой XML-текст, затем парсится через Fb2BlockParser.
 */
public class Fb2HistoryJax {

    @JacksonXmlText
    public String rawXml;
}
