package org.tehlab.whitek0t.fictionbook.internal.parser.jackson;

import org.tehlab.whitek0t.fictionbook.internal.reader.fb2.Fb2Reader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;

/**
 * Утилиты захвата сырого XML для mixed-content элементов {@code <annotation>} и
 * {@code <history>}.
 *
 * <p><b>Зачем это нужно.</b> Jackson XML маппит {@code <description>} в структурные
 * поля, но mixed content (параграфы с форматированием внутри annotation/history) он
 * через {@code @JacksonXmlText} захватить не может — текстовый аксессор видит только
 * прямой текст элемента, но не вложенную разметку {@code <p>…</p>}. Поэтому такие
 * блоки терялись при чтении, хотя записывались.</p>
 *
 * <p><b>Подход.</b> Поддерево {@code <description>} сериализуется в строку
 * namespace-unaware способом (как его и читает {@link Fb2Reader}: префикс {@code l:}
 * остаётся частью имени атрибута, объявления xmlns — обычными атрибутами). Из этой же
 * строки вытаскивается внутренний XML annotation/history и скармливается обратно в
 * {@code Fb2BlockParser.parseXmlFragment} через существующий путь маппинга.</p>
 */
public final class MixedContentCapture {

    private MixedContentCapture() {
    }

    /**
     * Сериализует элемент, на котором стоит {@code reader} (START_ELEMENT), вместе со
     * всеми потомками в строку. Reader остаётся на парном END_ELEMENT — ровно так же,
     * как его оставлял прежний прямой вызов Jackson, чтобы основной цикл чтения
     * продолжился корректно.
     */
    public static String serializeElement(XMLStreamReader reader) throws XMLStreamException {
        StringBuilder sb = new StringBuilder(256);
        int depth = 0;
        int event = reader.getEventType();
        while (true) {
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    writeStart(sb, reader);
                    depth++;
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    writeEnd(sb, reader);
                    depth--;
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA ->
                        writeText(sb, reader.getText());
                default -> { /* комментарии, PI и пр. опускаем */ }
            }
            if (depth == 0) {
                break; // только что записан парный END_ELEMENT
            }
            event = reader.next();
        }
        return sb.toString();
    }

    /**
     * Возвращает внутренний XML (только потомки, без самого тега) первого элемента
     * с именем {@code localName} внутри {@code containerXml}, либо {@code null}, если
     * элемент не найден или пуст.
     *
     * @param factory namespace-unaware фабрика (та же, что и в {@link Fb2Reader})
     */
    public static String extractInnerXml(String containerXml, XMLInputFactory factory, String localName)
            throws XMLStreamException {
        if (containerXml == null || containerXml.isBlank()) {
            return null;
        }

        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(containerXml));
        try {
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT
                        && localName.equals(reader.getLocalName())) {
                    return serializeChildren(reader);
                }
            }
            return null;
        } finally {
            reader.close();
        }
    }

    /** Reader стоит на START_ELEMENT контейнера; сериализуем только его потомков. */
    private static String serializeChildren(XMLStreamReader reader) throws XMLStreamException {
        StringBuilder sb = new StringBuilder(128);
        int depth = 1; // мы внутри контейнера
        while (reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    writeStart(sb, reader);
                    depth++;
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    depth--;
                    if (depth == 0) {
                        String inner = sb.toString();
                        return inner.isBlank() ? null : inner;
                    }
                    writeEnd(sb, reader);
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA ->
                        writeText(sb, reader.getText());
                default -> { /* опускаем */ }
            }
        }
        String inner = sb.toString();
        return inner.isBlank() ? null : inner;
    }

    // ========================================================================
    // СЕРИАЛИЗАЦИЯ (namespace-unaware: имена и атрибуты пишутся как есть)
    // ========================================================================

    private static void writeStart(StringBuilder sb, XMLStreamReader reader) {
        sb.append('<').append(reader.getLocalName());
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            sb.append(' ').append(reader.getAttributeLocalName(i)).append("=\"");
            escapeAttr(sb, reader.getAttributeValue(i));
            sb.append('"');
        }
        sb.append('>');
    }

    private static void writeEnd(StringBuilder sb, XMLStreamReader reader) {
        sb.append("</").append(reader.getLocalName()).append('>');
    }

    private static void writeText(StringBuilder sb, String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                default -> sb.append(c);
            }
        }
    }

    private static void escapeAttr(StringBuilder sb, String value) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(c);
            }
        }
    }
}
