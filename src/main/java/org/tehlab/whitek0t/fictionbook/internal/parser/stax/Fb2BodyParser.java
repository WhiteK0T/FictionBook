package org.tehlab.whitek0t.fictionbook.internal.parser.stax;

import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.exception.InvalidFormatException;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Парсит элемент {@code <body>} и его дочерние {@code <section>}.
 *
 * <p>Не строит {@code AnchorIndex} — индекс создаётся по требованию
 * из готового DTO через {@code AnchorIndexBuilder.fromDto()}.</p>
 */
public class Fb2BodyParser {

    private final Fb2BlockParser blockParser;

    public Fb2BodyParser(Fb2BlockParser blockParser) {
        this.blockParser = blockParser;
    }

    /**
     * Парсит {@code <body>} со всеми вложенными секциями.
     *
     * @param xml      StAX-ридер, установленный на открывающий тег {@code <body>}
     * @param fileName имя файла (для сообщений об ошибках)
     * @return готовый {@link BodyDto}
     */
    public BodyDto parseBody(XMLStreamReader xml, String fileName)
            throws XMLStreamException, FictionBookException {

        String bodyName = xml.getAttributeValue(null, "name"); // null или "notes"
        List<Section> sections = new ArrayList<>();

        while (xml.hasNext()) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    String tag = xml.getLocalName();
                    if ("section".equals(tag)) {
                        sections.add(parseSection(xml, fileName));
                    } else {
                        // Прощающий режим: пропускаем неизвестные элементы внутри <body>
                        blockParser.skipUnknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if ("body".equals(xml.getLocalName())) {
                        return new BodyDto(bodyName, List.copyOf(sections));
                    }
                }
            }
        }

        throw InvalidFormatException.missingElement(fileName, "</body>");
    }

    /**
     * Парсит {@code <section>} рекурсивно (с вложенными секциями).
     */
    private Section parseSection(XMLStreamReader xml, String fileName)
            throws XMLStreamException, FictionBookException {

        String id = xml.getAttributeValue(null, "id");

        List<BlockElement> title = new ArrayList<>();
        List<BlockElement> content = new ArrayList<>();
        List<Section> subSections = new ArrayList<>();

        while (xml.hasNext()) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    String tag = xml.getLocalName();
                    switch (tag) {
                        case "title" -> {
                            title.addAll(blockParser.parseBlockContainer(xml, "title", fileName));
                        }
                        case "section" -> subSections.add(parseSection(xml, fileName));
                        // ✅ Используем parseBlock для всех блочных элементов
                        case "p", "empty-line", "poem", "table", "subtitle", "cite", "epigraph" -> {
                            BlockElement block = blockParser.parseBlock(xml, tag, fileName);
                            if (block != null) {
                                content.add(block);
                            }
                        }
                        default -> blockParser.skipUnknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if ("section".equals(xml.getLocalName())) {
                        return new Section(
                                id,
                                List.copyOf(title),
                                List.copyOf(content),
                                List.copyOf(subSections),
                                Map.of() // metadata зарезервировано под будущий CSS
                        );
                    }
                }
            }
        }

        throw InvalidFormatException.missingElement(fileName, "</section>");
    }
}