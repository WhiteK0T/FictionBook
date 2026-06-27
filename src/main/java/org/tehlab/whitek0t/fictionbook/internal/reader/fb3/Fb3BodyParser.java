package org.tehlab.whitek0t.fictionbook.internal.reader.fb3;

import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.internal.parser.stax.Fb2BlockParser;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Парсит FB3-часть {@code body.xml} в {@link BodyDto}.
 *
 * <p>Структура FB3-тела близка к FB2: корень {@code <fb3-body>} содержит секции
 * {@code <section>}, а блочные/инлайновые элементы (за вычетом FB3-алиасов
 * {@code <img>} и {@code <blockquote>}) совпадают с FB2 по локальным именам тегов.
 * Поэтому блоки разбираются переиспользуемым {@link Fb2BlockParser}.</p>
 *
 * <p>Парсер namespace-unaware (как и FB2-ридер) и работает по локальным именам,
 * игнорируя префиксы пространств имён FB3. Режим прощающий: неизвестные элементы
 * пропускаются. Заголовок книги уровня {@code <fb3-body>/<title>} в модель не
 * переносится (в {@link BodyDto} нет поля заголовка — как и для FB2-тела).</p>
 */
final class Fb3BodyParser {

    private final XMLInputFactory factory;
    private final Fb2BlockParser blockParser;

    Fb3BodyParser(XMLInputFactory factory, Fb2BlockParser blockParser) {
        this.factory = factory;
        this.blockParser = blockParser;
    }

    /**
     * Разбирает {@code body.xml} в список тел книги.
     *
     * <p>FB3 хранит основной текст и сноски в отдельных частях, поэтому одна часть
     * даёт ровно одно {@link BodyDto} (без имени). Сноски подключаются вызывающим
     * кодом отдельно.</p>
     *
     * @param xml      байты части {@code body.xml}
     * @param bodyName имя тела ({@code null} для основного, {@code "notes"} для сносок)
     * @param fileName имя исходного файла (для сообщений об ошибках)
     * @return список с одним телом; пустой список, если корень не найден
     * @throws FictionBookException при ошибке разбора XML
     */
    List<BodyDto> parse(byte[] xml, String bodyName, String fileName) throws FictionBookException {
        try {
            XMLStreamReader r = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
            try {
                List<Section> sections = new ArrayList<>();
                boolean inBody = false;

                while (r.hasNext()) {
                    int event = r.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String tag = r.getLocalName();
                        if (!inBody) {
                            if ("fb3-body".equals(tag) || "body".equals(tag)) {
                                inBody = true;
                            }
                            // до корня ничего не делаем
                        } else if ("section".equals(tag)) {
                            sections.add(parseSection(r, fileName));
                        } else {
                            // <title>, <epigraph> уровня тела и прочее — пропускаем
                            blockParser.skipUnknownElement(r);
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT && inBody
                            && ("fb3-body".equals(r.getLocalName()) || "body".equals(r.getLocalName()))) {
                        break;
                    }
                }

                if (!inBody) {
                    return List.of();
                }
                return List.of(new BodyDto(bodyName, List.copyOf(sections)));
            } finally {
                r.close();
            }
        } catch (XMLStreamException e) {
            throw new FictionBookException("XML parsing error in FB3 body of " + fileName, e);
        }
    }

    /**
     * Открывает потоковый курсор по секциям {@code body.xml} (для {@code Fb3Streamer}):
     * секции верхнего уровня выдаются по одной, без удержания всего дерева тела в памяти.
     *
     * @param xml      байты части тела ({@code body.xml} или {@code notes.xml})
     * @param fileName имя исходного файла (для сообщений об ошибках)
     * @return курсор секций
     * @throws FictionBookException при ошибке инициализации StAX-ридера
     */
    SectionCursor cursor(byte[] xml, String fileName) throws FictionBookException {
        try {
            XMLStreamReader r = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
            return new SectionCursor(r, fileName);
        } catch (XMLStreamException e) {
            throw new FictionBookException("XML parsing error in FB3 body of " + fileName, e);
        }
    }

    /**
     * Ленивый курсор по секциям верхнего уровня одной части тела. Спускается до корня
     * ({@code <fb3-body>}/{@code <body>}), затем на каждый {@link #next()} отдаёт
     * следующую {@code <section>}; элементы уровня тела ({@code <title>} и т.п.)
     * пропускаются. По исчерпании части — {@code null}.
     */
    final class SectionCursor implements AutoCloseable {

        private final XMLStreamReader r;
        private final String fileName;
        private boolean inBody = false;
        private boolean done = false;

        private SectionCursor(XMLStreamReader r, String fileName) {
            this.r = r;
            this.fileName = fileName;
        }

        /**
         * @return следующая секция верхнего уровня или {@code null} в конце части
         * @throws FictionBookException при ошибке разбора XML
         */
        Section next() throws FictionBookException {
            if (done) {
                return null;
            }
            try {
                while (r.hasNext()) {
                    int event = r.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String tag = r.getLocalName();
                        if (!inBody) {
                            if ("fb3-body".equals(tag) || "body".equals(tag)) {
                                inBody = true;
                            }
                        } else if ("section".equals(tag)) {
                            return parseSection(r, fileName);
                        } else {
                            blockParser.skipUnknownElement(r);
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT && inBody
                            && ("fb3-body".equals(r.getLocalName()) || "body".equals(r.getLocalName()))) {
                        done = true;
                        return null;
                    }
                }
                done = true;
                return null;
            } catch (XMLStreamException e) {
                throw new FictionBookException("XML parsing error in FB3 body of " + fileName, e);
            }
        }

        @Override
        public void close() throws XMLStreamException {
            r.close();
        }
    }

    private Section parseSection(XMLStreamReader r, String fileName) throws XMLStreamException {

        String id = r.getAttributeValue(null, "id");

        List<BlockElement> title = new ArrayList<>();
        List<BlockElement> content = new ArrayList<>();
        List<Section> subSections = new ArrayList<>();

        while (r.hasNext()) {
            int event = r.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    String tag = r.getLocalName();
                    switch (tag) {
                        case "title" -> title.addAll(blockParser.parseBlockContainer(r, "title", fileName));
                        case "section" -> subSections.add(parseSection(r, fileName));
                        case "p", "empty-line", "poem", "table",
                             "subtitle", "cite", "blockquote", "epigraph", "image", "img" -> {
                            BlockElement block = blockParser.parseBlock(r, tag, fileName);
                            if (block != null) {
                                content.add(block);
                            }
                        }
                        default -> blockParser.skipUnknownElement(r);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if ("section".equals(r.getLocalName())) {
                        return new Section(
                                id,
                                List.copyOf(title),
                                List.copyOf(content),
                                List.copyOf(subSections),
                                Map.of()
                        );
                    }
                }
                default -> { /* CHARACTERS между блоками игнорируем */ }
            }
        }

        // Прощающий режим: EOF внутри секции
        return new Section(id, List.copyOf(title), List.copyOf(content),
                List.copyOf(subSections), Map.of());
    }
}
