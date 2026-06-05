package org.tehlab.whitek0t.fictionbook.internal.reader.fb3;

import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;
import org.tehlab.whitek0t.fictionbook.dto.description.Author;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.dto.description.DocumentInfo;
import org.tehlab.whitek0t.fictionbook.dto.description.Sequence;
import org.tehlab.whitek0t.fictionbook.dto.description.TitleInfo;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.internal.parser.stax.Fb2BlockParser;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Разбирает FB3-часть {@code description.xml} в {@link Description}.
 *
 * <p>Схема FB3-описания (пространство имён {@code .../FictionBook3/description})
 * отличается от FB2: название лежит в {@code <title>/<main>}, авторы — в
 * {@code <fb3-relations>/<subject link="author">}, жанры — в
 * {@code <fb3-classification>/<subject>}. Спецификация FB3 — черновик, поэтому
 * парсер намеренно прощающий: извлекает известные поля и игнорирует остальное,
 * никогда не падая на незнакомых элементах.</p>
 *
 * <p>Парсер namespace-unaware и работает по локальным именам тегов.</p>
 */
final class Fb3DescriptionParser {

    private final XMLInputFactory factory;
    private final Fb2BlockParser blockParser;

    Fb3DescriptionParser(XMLInputFactory factory, Fb2BlockParser blockParser) {
        this.factory = factory;
        this.blockParser = blockParser;
    }

    /**
     * Разбирает {@code description.xml}.
     *
     * @param xml           байты части {@code description.xml}
     * @param fileName      имя исходного файла (для сообщений об ошибках)
     * @param coverImageIds id обложек (без {@code #}), вычисленные ридером из OPC-связей
     * @return заполненное описание книги
     * @throws FictionBookException при ошибке разбора XML
     */
    Description parse(byte[] xml, String fileName, List<String> coverImageIds)
            throws FictionBookException {
        try {
            XMLStreamReader r = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
            try {
                String bookTitle = null;
                String lang = null;
                String docId = null;
                String version = null;
                Sequence sequence = null;
                List<Author> authors = new ArrayList<>();
                List<String> genres = new ArrayList<>();
                List<BlockElement> annotation = new ArrayList<>();

                while (r.hasNext()) {
                    if (r.next() != XMLStreamConstants.START_ELEMENT) continue;
                    switch (r.getLocalName()) {
                        case "fb3-description" -> {
                            // id/version в FB3 часто заданы атрибутами корня;
                            // дочерние <id>/<version>, если есть, их перекроют ниже.
                            docId = r.getAttributeValue(null, "id");
                            version = r.getAttributeValue(null, "version");
                        }
                        case "title" -> {
                            if (bookTitle == null) {
                                bookTitle = readTitleMain(r);
                            } else {
                                blockParser.skipUnknownElement(r);
                            }
                        }
                        case "fb3-relations" -> readRelations(r, authors);
                        case "subject" -> {
                            // <subject> вне <fb3-relations> внутри <fb3-classification> — жанр
                            String g = readText(r, "subject");
                            if (g != null) genres.add(g);
                        }
                        case "lang" -> lang = readText(r, "lang");
                        case "id" -> docId = readText(r, "id");
                        case "version" -> version = readText(r, "version");
                        case "sequence" -> {
                            sequence = readSequence(r);
                        }
                        case "annotation" ->
                                annotation.addAll(blockParser.parseBlockContainer(r, "annotation", fileName));
                        default -> { /* fb3-classification, document-info и пр. — заходим внутрь */ }
                    }
                }

                TitleInfo titleInfo = new TitleInfo(
                        List.copyOf(authors),
                        List.copyOf(genres),
                        bookTitle,
                        List.copyOf(annotation),
                        lang,
                        null,
                        sequence,
                        coverImageIds == null ? List.of() : List.copyOf(coverImageIds)
                );

                DocumentInfo documentInfo = new DocumentInfo(
                        List.of(), null, null, null, null,
                        docId, version, List.of()
                );

                return new Description(titleInfo, documentInfo, null);
            } finally {
                r.close();
            }
        } catch (XMLStreamException e) {
            throw new FictionBookException("XML parsing error in FB3 description of " + fileName, e);
        }
    }

    // ========================================================================
    // ВНУТРЕННЕЕ
    // ========================================================================

    /** Читает {@code <title>/<main>} (склеивая текст), пропуская остальное до {@code </title>}. */
    private String readTitleMain(XMLStreamReader r) throws XMLStreamException {
        String main = null;
        int depth = 1;
        while (r.hasNext() && depth > 0) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("main".equals(r.getLocalName())) {
                    main = readText(r, "main");
                } else {
                    depth++;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return main;
    }

    /** Читает авторов из {@code <fb3-relations>}: каждый {@code <subject link="author">}. */
    private void readRelations(XMLStreamReader r, List<Author> authors) throws XMLStreamException {
        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT && "subject".equals(r.getLocalName())) {
                String link = r.getAttributeValue(null, "link");
                Author author = readSubjectAsAuthor(r);
                if (author != null && (link == null || link.toLowerCase().contains("author"))) {
                    authors.add(author);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT
                    && "fb3-relations".equals(r.getLocalName())) {
                return;
            }
        }
    }

    /**
     * Читает {@code <subject>} как автора. Берёт {@code <first-name>}/{@code <middle-name>}/
     * {@code <last-name>}; если их нет — раскладывает {@code <title>/<main>} на части по пробелам.
     */
    private Author readSubjectAsAuthor(XMLStreamReader r) throws XMLStreamException {
        String first = null, middle = null, last = null, titleMain = null;
        int depth = 1;
        while (r.hasNext() && depth > 0) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (r.getLocalName()) {
                    case "first-name" -> first = readText(r, "first-name");
                    case "middle-name" -> middle = readText(r, "middle-name");
                    case "last-name" -> last = readText(r, "last-name");
                    case "title" -> titleMain = readTitleMain(r);
                    default -> depth++;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }

        if (first == null && middle == null && last == null) {
            if (titleMain == null || titleMain.isBlank()) {
                return null;
            }
            return splitName(titleMain);
        }
        return new Author(first, middle, last);
    }

    /** Раскладывает «Имя Отчество Фамилия» на части (последнее слово — фамилия). */
    private Author splitName(String full) {
        String[] parts = full.trim().split("\\s+");
        return switch (parts.length) {
            case 1 -> new Author(null, null, parts[0]);
            case 2 -> new Author(parts[0], null, parts[1]);
            default -> new Author(parts[0], parts[1],
                    String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length)));
        };
    }

    private Sequence readSequence(XMLStreamReader r) throws XMLStreamException {
        String name = r.getAttributeValue(null, "name");
        if (name == null) name = r.getAttributeValue(null, "title");
        String num = r.getAttributeValue(null, "number");
        if (num == null) num = r.getAttributeValue(null, "value");
        skipToEnd(r, "sequence");
        if (name == null) return null;
        Integer number = null;
        if (num != null) {
            try {
                number = Integer.parseInt(num.trim());
            } catch (NumberFormatException ignored) {
                // нечисловой номер — оставляем null
            }
        }
        return new Sequence(name, number);
    }

    /** Извлекает текстовое содержимое элемента {@code tag}, игнорируя вложенную разметку. */
    private String readText(XMLStreamReader r, String tag) throws XMLStreamException {
        StringBuilder sb = new StringBuilder();
        int depth = 1;
        while (r.hasNext() && depth > 0) {
            int event = r.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> depth++;
                case XMLStreamConstants.END_ELEMENT -> depth--;
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA,
                     XMLStreamConstants.SPACE -> sb.append(r.getText());
                default -> { /* прочее игнорируем */ }
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private void skipToEnd(XMLStreamReader r, String tag) throws XMLStreamException {
        int depth = 1;
        while (r.hasNext() && depth > 0) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) depth++;
            else if (event == XMLStreamConstants.END_ELEMENT) depth--;
        }
    }
}
