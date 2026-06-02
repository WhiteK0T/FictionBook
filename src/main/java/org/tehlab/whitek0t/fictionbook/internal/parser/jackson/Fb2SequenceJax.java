package org.tehlab.whitek0t.fictionbook.internal.parser.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

// ============================================================================
// СЕРИЯ (SEQUENCE)
// ============================================================================

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Fb2SequenceJax {

    @JacksonXmlProperty(isAttribute = true, localName = "name")
    public String name;

    @JacksonXmlProperty(isAttribute = true, localName = "number")
    public Integer number;
}
