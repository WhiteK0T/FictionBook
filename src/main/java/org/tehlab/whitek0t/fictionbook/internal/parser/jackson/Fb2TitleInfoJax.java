package org.tehlab.whitek0t.fictionbook.internal.parser.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

// ============================================================================
// TITLE-INFO (Информация о книге)
// ============================================================================

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Fb2TitleInfoJax {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "genre")
    public List<String> genres;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "author")
    public List<Fb2AuthorJax> authors;

    @JacksonXmlProperty(localName = "book-title")
    public String bookTitle;

    @JacksonXmlProperty(localName = "annotation")
    public Fb2AnnotationJax annotation;

    @JacksonXmlProperty(localName = "lang")
    public String lang;

    @JacksonXmlProperty(localName = "src-lang")
    public String srcLang;

    @JacksonXmlProperty(localName = "sequence")
    public Fb2SequenceJax sequence;

    @JacksonXmlProperty(localName = "coverpage")
    public Fb2CoverpageJax coverpage;
}