package org.tehlab.whitek0t.fictionbook.internal.parser.jackson;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * DTO для элемента {@code <image>} внутри {@code <coverpage>}.
 *
 * <p>Поддерживает два варианта написания атрибута href:</p>
 * <ul>
 *   <li>С namespace: {@code <image l:href="#cover"/>} → Jackson парсит через namespace</li>
 *   <li>С префиксом как имя: {@code <image l:href="#cover"/>} → читается напрямую</li>
 * </ul>
 */
public class Fb2ImageRefJax {

    /**
     * Атрибут href с namespace xlink (основной вариант).
     */
    @JacksonXmlProperty(
            isAttribute = true,
            namespace = "http://www.w3.org/1999/xlink",
            localName = "href"
    )
    public String href;

    /**
     * Fallback: атрибут href с префиксом l: как часть имени.
     * Используется, когда Jackson не распознаёт namespace корректно.
     */
    @JacksonXmlProperty(isAttribute = true, localName = "l:href")
    public String hrefWithPrefix;

    @JacksonXmlProperty(isAttribute = true, localName = "alt")
    public String alt;

    /**
     * Возвращает эффективный href, проверяя оба варианта.
     * Приоритет: namespace-вариант > префикс-вариант.
     */
    public String getEffectiveHref() {
        if (href != null && !href.isBlank()) return href;
        if (hrefWithPrefix != null && !hrefWithPrefix.isBlank()) return hrefWithPrefix;
        return null;
    }
}