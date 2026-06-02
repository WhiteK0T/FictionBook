package org.tehlab.whitek0t.fictionbook.internal.parser.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

// ============================================================================
// КОРНЕВОЙ ЭЛЕМЕНТ
// ============================================================================

@JsonInclude(JsonInclude.Include.NON_NULL)
@JacksonXmlRootElement(localName = "description", namespace = "http://www.gribuser.ru/xml/fictionbook/2.0")
public class Fb2DescriptionJax {

    @JacksonXmlProperty(localName = "title-info")
    public Fb2TitleInfoJax titleInfo;

    @JacksonXmlProperty(localName = "document-info")
    public Fb2DocumentInfoJax documentInfo;

    @JacksonXmlProperty(localName = "publish-info")
    public Fb2PublishInfoJax publishInfo;
}
