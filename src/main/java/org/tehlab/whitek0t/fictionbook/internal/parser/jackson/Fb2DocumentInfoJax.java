package org.tehlab.whitek0t.fictionbook.internal.parser.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

// ============================================================================
// DOCUMENT-INFO (Информация о документе)
// ============================================================================

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Fb2DocumentInfoJax {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "author")
    public List<Fb2AuthorJax> authors;

    @JacksonXmlProperty(localName = "program-used")
    public String programUsed;

    @JacksonXmlProperty(localName = "date")
    public String date;

    @JacksonXmlProperty(localName = "src-url")
    public String srcUrl;

    @JacksonXmlProperty(localName = "src-ocr")
    public String srcOcr;

    @JacksonXmlProperty(localName = "id")
    public String id;

    @JacksonXmlProperty(localName = "version")
    public String version;

    @JacksonXmlProperty(localName = "history")
    public Fb2HistoryJax history;
}
