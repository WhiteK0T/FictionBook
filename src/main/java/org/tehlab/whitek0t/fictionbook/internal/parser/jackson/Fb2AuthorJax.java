package org.tehlab.whitek0t.fictionbook.internal.parser.jackson;

// ============================================================================
// АВТОР
// ============================================================================

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Fb2AuthorJax {

    @JacksonXmlProperty(localName = "first-name")
    public String firstName;

    @JacksonXmlProperty(localName = "middle-name")
    public String middleName;

    @JacksonXmlProperty(localName = "last-name")
    public String lastName;
}
