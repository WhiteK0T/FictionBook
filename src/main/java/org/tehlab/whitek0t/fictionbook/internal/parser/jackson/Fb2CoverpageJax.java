package org.tehlab.whitek0t.fictionbook.internal.parser.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

// ============================================================================
// ОБЛОЖКА (COVERPAGE)
// ============================================================================

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Fb2CoverpageJax {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "image")
    public List<Fb2ImageRefJax> images;
}
