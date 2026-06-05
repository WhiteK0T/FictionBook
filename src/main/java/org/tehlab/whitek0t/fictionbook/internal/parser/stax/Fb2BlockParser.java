package org.tehlab.whitek0t.fictionbook.internal.parser.stax;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tehlab.whitek0t.fictionbook.dto.block.*;
import org.tehlab.whitek0t.fictionbook.dto.inline.*;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Парсер блочных элементов FB2.
 *
 * <p>Использует паттерн "Стек билдеров" для обработки mixed content
 * (текст + вложенные теги в любом порядке).</p>
 *
 * <p>Поддерживаемые блочные элементы:</p>
 * <ul>
 *   <li>{@code <p>} — параграф</li>
 *   <li>{@code <empty-line>} — пустая строка</li>
 *   <li>{@code <subtitle>} — подзаголовок</li>
 *   <li>{@code <poem>} — стихотворение (со строфами и стихами)</li>
 *   <li>{@code <table>} — таблица (со строками и ячейками)</li>
 *   <li>{@code <cite>} — цитата (с автором)</li>
 *   <li>{@code <epigraph>} — эпиграф (с автором)</li>
 * </ul>
 *
 * <p><b>Прощающий режим:</b> неизвестные теги пропускаются через
 * {@link #skipUnknownElement(XMLStreamReader)}, не ломая парсинг.</p>
 */
public class Fb2BlockParser {

    private static final Logger log = LoggerFactory.getLogger(Fb2BlockParser.class);
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";

    private final XMLInputFactory factory;

    public Fb2BlockParser(XMLInputFactory factory) {
        this.factory = factory;
    }

    // ========================================================================
    // ГЛАВНАЯ ТОЧКА ВХОДА
    // ========================================================================

    /**
     * Парсит блочный элемент по его тегу.
     *
     * @param xml      StAX-ридер, установленный на START_ELEMENT
     * @param tag      имя тега (без namespace)
     * @param fileName имя файла (для сообщений об ошибках)
     * @return готовый BlockElement или null для неизвестных тегов
     */
    public BlockElement parseBlock(XMLStreamReader xml, String tag, String fileName)
            throws XMLStreamException {
        return switch (tag) {
            case "p" -> parseParagraph(xml);
            case "empty-line" -> {
                skipUnknownElement(xml);
                yield new EmptyLine();
            }
            case "subtitle" -> parseSubtitle(xml);
            case "poem" -> parsePoem(xml, fileName);
            case "table" -> parseTable(xml);
            // FB3-алиас: <blockquote> структурно совпадает с FB2 <cite>.
            case "cite", "blockquote" -> parseCite(xml, tag, fileName);
            case "epigraph" -> parseEpigraph(xml, fileName);
            default -> {
                skipUnknownElement(xml);
                yield null;
            }
        };
    }

    /**
     * Парсит контейнер блочных элементов (например, {@code <title>}).
     *
     * @param xml           StAX-ридер
     * @param containerTag  имя контейнерного тега
     * @param fileName      имя файла
     * @return список блочных элементов
     */
    public List<BlockElement> parseBlockContainer(XMLStreamReader xml,
                                                  String containerTag,
                                                  String fileName)
            throws XMLStreamException {
        List<BlockElement> blocks = new ArrayList<>();

        while (xml.hasNext()) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    String tag = xml.getLocalName();
                    BlockElement block = parseBlock(xml, tag, fileName);
                    if (block != null) {
                        blocks.add(block);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if (containerTag.equals(xml.getLocalName())) {
                        return List.copyOf(blocks);
                    }
                }
            }
        }
        return List.copyOf(blocks);
    }

    /**
     * Парсит фрагмент XML-текста в список блочных элементов.
     * Используется для mixed content (annotation, history, etc.).
     *
     * @param xmlFragment строка вида "<p>Текст</p><p>Ещё текст</p>"
     * @return список BlockElement
     */
    public List<BlockElement> parseXmlFragment(String xmlFragment) throws XMLStreamException {
        if (xmlFragment == null || xmlFragment.isBlank()) {
            return List.of();
        }

        // Оборачиваем фрагмент в корневой элемент для валидного XML
        String wrappedXml = "<root>" + xmlFragment + "</root>";
        XMLStreamReader xml = factory.createXMLStreamReader(new StringReader(wrappedXml));

        List<BlockElement> blocks = new ArrayList<>();

        // Пропускаем START_ELEMENT для <root>
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.START_ELEMENT && "root".equals(xml.getLocalName())) {
                break;
            }
        }

        // Парсим содержимое
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tag = xml.getLocalName();
                if (!"root".equals(tag)) {
                    BlockElement block = parseBlock(xml, tag, "<fragment>");
                    if (block != null) {
                        blocks.add(block);
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "root".equals(xml.getLocalName())) {
                break;
            }
        }

        xml.close();
        return List.copyOf(blocks);
    }

    // ========================================================================
    // ПАРАГРАФ И ПОДЗАГОЛОВОК
    // ========================================================================

    /**
     * Парсит {@code <p>} — параграф с инлайн-элементами.
     * Использует стек билдеров для обработки mixed content.
     */
    private Paragraph parseParagraph(XMLStreamReader xml) throws XMLStreamException {
        Deque<NodeBuilder> stack = new ArrayDeque<>();
        stack.push(new ParagraphBuilder());

        while (xml.hasNext()) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    NodeBuilder child = createInlineBuilder(xml.getLocalName(), xml);
                    stack.push(child);
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA,
                     XMLStreamConstants.SPACE -> {
                    String text = xml.getText();
                    stack.peek().appendText(text);
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if ("p".equals(xml.getLocalName()) && stack.size() == 1) {
                        return (Paragraph) stack.pop().build();
                    }
                    NodeBuilder completed = stack.pop();
                    Object built = completed.build();
                    if (built != null) {
                        stack.peek().addChild(built);
                    }
                }
            }
        }
        // Прощающий режим: EOF внутри параграфа
        return (Paragraph) stack.peek().build();
    }

    private BlockElement parseSubtitle(XMLStreamReader xml) throws XMLStreamException {
        // Subtitle имеет ту же структуру, что и paragraph
        Deque<NodeBuilder> stack = new ArrayDeque<>();
        stack.push(new ParagraphBuilder());

        while (xml.hasNext()) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    NodeBuilder child = createInlineBuilder(xml.getLocalName(), xml);
                    stack.push(child);
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA,
                     XMLStreamConstants.SPACE -> {
                    String text = xml.getText();
                    stack.peek().appendText(text);
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if ("subtitle".equals(xml.getLocalName()) && stack.size() == 1) {
                        return (Paragraph) stack.pop().build();
                    }
                    NodeBuilder completed = stack.pop();
                    Object built = completed.build();
                    if (built != null) {
                        stack.peek().addChild(built);
                    }
                }
            }
        }
        return (Paragraph) stack.peek().build();
    }

    // ========================================================================
    // СТИХОТВОРЕНИЕ (POEM)
    // ========================================================================

    /**
     * Парсит {@code <poem>} со всеми дочерними элементами:
     * title, epigraph, stanza (с verse), text-author, date.
     */
    private Poem parsePoem(XMLStreamReader xml, String fileName) throws XMLStreamException {
        String id = xml.getAttributeValue(null, "id");

        List<BlockElement> title = new ArrayList<>();
        List<Stanza> stanzas = new ArrayList<>();
        List<BlockElement> epigraph = new ArrayList<>();
        String author = null;

        while (xml.hasNext()) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    String tag = xml.getLocalName();
                    switch (tag) {
                        case "title" ->
                                title.addAll(parseBlockContainer(xml, "title", fileName));
                        case "epigraph" -> {
                            Epigraph epi = parseEpigraph(xml, fileName);
                            epigraph.add(epi);
                        }
                        case "stanza" -> stanzas.add(parseStanza(xml));
                        case "text-author" ->
                                author = readTextContent(xml, "text-author");
                        case "date" ->
                                readTextContent(xml, "date"); // читаем, но не сохраняем
                        default -> skipUnknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if ("poem".equals(xml.getLocalName())) {
                        return new Poem(
                                id,
                                List.copyOf(title),
                                List.copyOf(stanzas),
                                List.copyOf(epigraph),
                                author
                        );
                    }
                }
            }
        }
        return new Poem(id, List.copyOf(title), List.copyOf(stanzas),
                List.copyOf(epigraph), author);
    }

    /**
     * Парсит {@code <stanza>} — строфу стихотворения.
     */
    private Stanza parseStanza(XMLStreamReader xml) throws XMLStreamException {
        List<Verse> verses = new ArrayList<>();

        while (xml.hasNext()) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    String tag = xml.getLocalName();
                    if ("v".equals(tag)) {
                        verses.add(parseVerse(xml));
                    } else {
                        skipUnknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if ("stanza".equals(xml.getLocalName())) {
                        return new Stanza(List.copyOf(verses));
                    }
                }
            }
        }
        return new Stanza(List.copyOf(verses));
    }

    /**
     * Парсит {@code <v>} — строку стихотворения.
     * Структура аналогична {@code <p>}, но используется VerseBuilder.
     */
    private Verse parseVerse(XMLStreamReader xml) throws XMLStreamException {
        Deque<NodeBuilder> stack = new ArrayDeque<>();
        stack.push(new VerseBuilder());

        while (xml.hasNext()) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    NodeBuilder child = createInlineBuilder(xml.getLocalName(), xml);
                    stack.push(child);
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA,
                     XMLStreamConstants.SPACE -> {
                    stack.peek().appendText(xml.getText());
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if ("v".equals(xml.getLocalName()) && stack.size() == 1) {
                        return (Verse) stack.pop().build();
                    }
                    NodeBuilder completed = stack.pop();
                    Object built = completed.build();
                    if (built != null) {
                        stack.peek().addChild(built);
                    }
                }
            }
        }
        return (Verse) stack.peek().build();
    }

    // ========================================================================
    // ТАБЛИЦА (TABLE)
    // ========================================================================

    /**
     * Парсит {@code <table>} со строками {@code <tr>} и ячейками {@code <td>}.
     * Ячейки могут содержать как явные {@code <p>}, так и "грязный" текст —
     * {@link TableCellBuilder} автоматически оборачивает его в параграф.
     */
    private Table parseTable(XMLStreamReader xml) throws XMLStreamException {
        List<TableRow> rows = new ArrayList<>();

        while (xml.hasNext()) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    String tag = xml.getLocalName();
                    if ("tr".equals(tag)) {
                        rows.add(parseTableRow(xml));
                    } else {
                        skipUnknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if ("table".equals(xml.getLocalName())) {
                        return new Table(List.copyOf(rows));
                    }
                }
            }
        }
        return new Table(List.copyOf(rows));
    }

    private TableRow parseTableRow(XMLStreamReader xml) throws XMLStreamException {
        List<TableCell> cells = new ArrayList<>();

        while (xml.hasNext()) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    String tag = xml.getLocalName();
                    if ("td".equals(tag) || "th".equals(tag)) {
                        cells.add(parseTableCell(xml));
                    } else {
                        skipUnknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if ("tr".equals(xml.getLocalName())) {
                        return new TableRow(List.copyOf(cells));
                    }
                }
            }
        }
        return new TableRow(List.copyOf(cells));
    }

    /**
     * Парсит {@code <td>} (или {@code <th>}) с использованием {@link TableCellBuilder},
     * который автоматически оборачивает "грязный" текст в параграфы.
     */
    private TableCell parseTableCell(XMLStreamReader xml) throws XMLStreamException {
        TableCellBuilder builder = new TableCellBuilder();

        while (xml.hasNext()) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    String tag = xml.getLocalName();
                    if (isBlockTag(tag)) {
                        BlockElement block = parseBlock(xml, tag, "<td>");
                        if (block != null) {
                            builder.addChild(block);
                        }
                    } else if (isInlineTag(tag)) {
                        NodeBuilder inlineBuilder = createInlineBuilder(tag, xml);
                        Object inline = readInlineElement(xml, tag, inlineBuilder);
                        if (inline != null) {
                            builder.addChild(inline);
                        }
                    } else {
                        skipUnknownElement(xml);
                    }
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA,
                     XMLStreamConstants.SPACE -> {
                    builder.appendText(xml.getText());
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    String tag = xml.getLocalName();
                    if ("td".equals(tag) || "th".equals(tag)) {
                        return (TableCell) builder.build();
                    }
                }
            }
        }
        return (TableCell) builder.build();
    }

    // ========================================================================
    // ЦИТАТА И ЭПИГРАФ
    // ========================================================================

    /**
     * Парсит {@code <cite>} (или FB3-алиас {@code <blockquote>}) с текстом и автором.
     *
     * @param closingTag локальное имя элемента, по закрытию которого завершается разбор
     *                   ({@code "cite"} для FB2, {@code "blockquote"} для FB3)
     */
    private Cite parseCite(XMLStreamReader xml, String closingTag, String fileName)
            throws XMLStreamException {
        String id = xml.getAttributeValue(null, "id");

        List<BlockElement> content = new ArrayList<>();
        String author = null;

        while (xml.hasNext()) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    String tag = xml.getLocalName();
                    switch (tag) {
                        case "text-author" ->
                                author = readTextContent(xml, "text-author");
                        case "p", "empty-line", "poem", "table", "subtitle" -> {
                            BlockElement block = parseBlock(xml, tag, fileName);
                            if (block != null) content.add(block);
                        }
                        default -> skipUnknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if (closingTag.equals(xml.getLocalName())) {
                        return new Cite(id, List.copyOf(content), author);
                    }
                }
            }
        }
        return new Cite(id, List.copyOf(content), author);
    }

    /**
     * Парсит {@code <epigraph>} — структуру аналогичную {@code <cite>}.
     */
    private Epigraph parseEpigraph(XMLStreamReader xml, String fileName) throws XMLStreamException {
        String id = xml.getAttributeValue(null, "id");

        List<BlockElement> content = new ArrayList<>();
        String author = null;

        while (xml.hasNext()) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    String tag = xml.getLocalName();
                    switch (tag) {
                        case "text-author" ->
                                author = readTextContent(xml, "text-author");
                        case "p", "empty-line", "poem", "table", "subtitle" -> {
                            BlockElement block = parseBlock(xml, tag, fileName);
                            if (block != null) content.add(block);
                        }
                        default -> skipUnknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if ("epigraph".equals(xml.getLocalName())) {
                        return new Epigraph(id, List.copyOf(content), author);
                    }
                }
            }
        }
        return new Epigraph(id, List.copyOf(content), author);
    }

    // ========================================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ========================================================================

    /**
     * Читает содержимое элементов типа {@code <text-author>} или {@code <date>},
     * извлекая весь текст (игнорируя форматирование).
     */
    private String readTextContent(XMLStreamReader xml, String tagName) throws XMLStreamException {
        StringBuilder sb = new StringBuilder();
        int depth = 1;

        while (xml.hasNext() && depth > 0) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> depth++;
                case XMLStreamConstants.END_ELEMENT -> {
                    if (tagName.equals(xml.getLocalName()) && depth == 1) {
                        depth--;
                    } else {
                        depth--;
                    }
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA,
                     XMLStreamConstants.SPACE -> sb.append(xml.getText());
            }
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * Создаёт билдер для инлайн-элемента по его тегу.
     */
    private NodeBuilder createInlineBuilder(String tag, XMLStreamReader xml) {
        return switch (tag) {
            case "strong", "b" ->
                    new InlineContainerBuilder<>(Strong::new);
            case "emphasis", "i" ->
                    new InlineContainerBuilder<>(Emphasis::new);
            case "strikethrough", "s" ->
                    new InlineContainerBuilder<>(Strikethrough::new);
            case "sub" ->
                    new InlineContainerBuilder<>(Sub::new);
            case "sup" ->
                    new InlineContainerBuilder<>(Sup::new);
            case "a" -> {
                // ✅ Используем универсальный метод
                String href = getAttributeValue(xml, "href");
                String type = xml.getAttributeValue(null, "type");
                yield new LinkBuilder(href, type);
            }
            // <image> — FB2, <img> — FB3-алиас (тот же inline-смысл).
            case "image", "img" -> {
                // ✅ Используем универсальный метод
                String href = getAttributeValue(xml, "href");
                String alt = xml.getAttributeValue(null, "alt");
                yield new ImageBuilder(href, alt);
            }
            default -> new IgnoreBuilder();
        };
    }

    /**
     * Читает инлайн-элемент вместе с его содержимым.
     * Используется в TableCellBuilder для обработки inline внутри {@code <td>}.
     */
    private Object readInlineElement(XMLStreamReader xml, String tag, NodeBuilder builder)
            throws XMLStreamException {
        while (xml.hasNext()) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    NodeBuilder child = createInlineBuilder(xml.getLocalName(), xml);
                    Object nested = readInlineElement(xml, xml.getLocalName(), child);
                    if (nested != null) {
                        builder.addChild(nested);
                    }
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA,
                     XMLStreamConstants.SPACE -> {
                    builder.appendText(xml.getText());
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if (tag.equals(xml.getLocalName())) {
                        return builder.build();
                    }
                }
            }
        }
        return builder.build();
    }

    /**
     * Универсальный метод для чтения атрибутов, который корректно работает
     * как с включенным, так и с отключенным IS_NAMESPACE_AWARE.
     *
     * Проверяет:
     * 1. Атрибут в XLINK namespace (если namespace-aware)
     * 2. Атрибут без namespace
     * 3. Атрибут с префиксом "l:" (fallback для IS_NAMESPACE_AWARE = false)
     */
    private String getAttributeValue(XMLStreamReader xml, String localName) {
        // 1. Пробуем с namespace (работает, если IS_NAMESPACE_AWARE = true)
        String value = xml.getAttributeValue(XLINK_NS, localName);
        if (value != null) return value;

        // 2. Пробуем без namespace
        value = xml.getAttributeValue(null, localName);
        if (value != null) return value;

        // 3. Fallback: если namespace-aware отключен, префикс становится частью имени
        return xml.getAttributeValue(null, "l:" + localName);
    }

    /**
     * Пропускает элемент целиком (включая всех потомков).
     * Используется в прощающем режиме для неизвестных тегов.
     */
    public void skipUnknownElement(XMLStreamReader xml) throws XMLStreamException {
        int depth = 1;
        while (xml.hasNext() && depth > 0) {
            int e = xml.next();
            if (e == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (e == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    private boolean isBlockTag(String tag) {
        return switch (tag) {
            case "p", "empty-line", "poem", "table", "subtitle", "cite", "blockquote", "epigraph" -> true;
            default -> false;
        };
    }

    private boolean isInlineTag(String tag) {
        return switch (tag) {
            case "strong", "b", "emphasis", "i", "strikethrough", "s",
                 "sub", "sup", "a", "image", "img" -> true;
            default -> false;
        };
    }
}
