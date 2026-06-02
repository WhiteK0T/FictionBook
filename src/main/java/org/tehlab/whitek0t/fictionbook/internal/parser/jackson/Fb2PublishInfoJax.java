package org.tehlab.whitek0t.fictionbook.internal.parser.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

// ============================================================================
// PUBLISH-INFO (Издательская информация)
// ============================================================================

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Fb2PublishInfoJax {

    @JacksonXmlProperty(localName = "book-name")
    public String bookName;

    @JacksonXmlProperty(localName = "publisher")
    public String publisher;

    @JacksonXmlProperty(localName = "city")
    public String city;

    @JacksonXmlProperty(localName = "year")
    public String year;

    @JacksonXmlProperty(localName = "isbn")
    public String isbn;
}
