package org.tehlab.whitek0t.fictionbook.internal.writer.fb2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.*;
import org.tehlab.whitek0t.fictionbook.dto.description.*;
import org.tehlab.whitek0t.fictionbook.dto.inline.*;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.exception.InvalidFormatException;
import org.tehlab.whitek0t.fictionbook.internal.sanitizer.SanitizerPipeline;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

/**
 * Записывает {@link FictionBookDto} в FB2-файл.
 *
 * <p>Особенности реализации:</p>
 * <ul>
 *   <li><b>Автоматическая санитизация:</b> перед записью применяется {@link SanitizerPipeline}</li>
 *   <li><b>StAX (XMLStreamWriter):</b> быстрая потоковая запись без загрузки всего XML в память</li>
 *   <li><b>Стриминг base64:</b> бинарники кодируются чанками, не загружаясь целиком в RAM</li>
 *   <li><b>UTF-8:</b> явное указание кодировки в XML declaration и при записи</li>
 *   <li><b>Pretty print:</b> опциональное форматирование с отступами</li>
 * </ul>
 */
public class Fb2Writer {

    private static final Logger log = LoggerFactory.getLogger(Fb2Writer.class);

    private static final String FB2_NS = "http://www.gribuser.ru/xml/fictionbook/2.0";
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";
    private static final int BASE64_CHUNK_SIZE = 3 * 1024; // Кратно 3 для чистого base64

    private SanitizerPipeline sanitizerPipeline;
    private boolean prettyPrint = true;

    public Fb2Writer() {
        this.sanitizerPipeline = SanitizerPipeline.standard();
    }

    /**
     * Устанавливает пайплайн санитайзеров.
     * Передайте {@code null} для отключения санитизации.
     */
    public void setSanitizerPipeline(SanitizerPipeline pipeline) {
        this.sanitizerPipeline = pipeline;
    }

    /**
     * Включает/выключает pretty print (форматирование с отступами).
     */
    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /**
     * Записывает книгу в FB2-файл.
     *
     * @param book        DTO книги
     * @param destination путь к выходному файлу
     * @throws FictionBookException при ошибках записи
     */
    public void write(FictionBookDto book, Path destination) throws FictionBookException {
        String fileName = InvalidFormatException.extractFileName(destination);

        try {
            // 1. Санитизация
            FictionBookDto clean = sanitize(book);

            // 2. Запись
            try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(destination));
                 OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {

                writeToFb2(clean, writer, fileName);
            }

        } catch (FictionBookException e) {
            throw e;
        } catch (Exception e) {
            throw new FictionBookException("Failed to write FB2: " + destination, e);
        }
    }

    /**
     * Записывает книгу в OutputStream.
     */
    public void write(FictionBookDto book, OutputStream outputStream) throws FictionBookException {
        try {
            FictionBookDto clean = sanitize(book);
            OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            writeToFb2(clean, writer, "<stream>");
        } catch (FictionBookException e) {
            throw e;
        } catch (Exception e) {
            throw new FictionBookException("Failed to write FB2 to stream", e);
        }
    }

    // ========================================================================
    // ОСНОВНАЯ ЛОГИКА ЗАПИСИ
    // ========================================================================

    private FictionBookDto sanitize(FictionBookDto book) {
        if (sanitizerPipeline == null) {
            log.debug("Sanitization disabled, writing book as-is");
            return book;
        }

        log.debug("Applying sanitizer pipeline");
        return sanitizerPipeline.sanitize(book);
    }

    private void writeToFb2(FictionBookDto book, OutputStreamWriter writer, String fileName)
            throws XMLStreamException, IOException, FictionBookException {

        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLStreamWriter xml = factory.createXMLStreamWriter(writer);

        // XML declaration
        xml.writeStartDocument("UTF-8", "1.0");
        writeNewline(xml);

        // Корневой элемент <FictionBook>
        xml.writeStartElement("FictionBook");
        xml.writeDefaultNamespace(FB2_NS);
        xml.writeNamespace("l", XLINK_NS);
        writeNewline(xml);

        // <description>
        if (book.description() != null) {
            writeDescription(xml, book.description());
        }

        // <body> (может быть несколько: основной текст + notes)
        for (BodyDto body : book.bodies()) {
            writeBody(xml, body);
        }

        // <binary> (ресурсы)
        for (Resource resource : book.resources().values()) {
            writeBinary(xml, resource);
        }

        // Закрываем </FictionBook>
        xml.writeEndElement();
        writeNewline(xml);

        xml.writeEndDocument();
        xml.flush();
        xml.close();
    }

    // ========================================================================
    // ЗАПИСЬ <description>
    // ========================================================================

    private void writeDescription(XMLStreamWriter xml, Description desc) throws XMLStreamException {
        xml.writeStartElement("description");
        writeNewline(xml);

        if (desc.titleInfo() != null) {
            writeTitleInfo(xml, desc.titleInfo());
        }

        if (desc.documentInfo() != null) {
            writeDocumentInfo(xml, desc.documentInfo());
        }

        if (desc.publishInfo() != null) {
            writePublishInfo(xml, desc.publishInfo());
        }

        xml.writeEndElement(); // </description>
        writeNewline(xml);
    }

    private void writeTitleInfo(XMLStreamWriter xml, TitleInfo info) throws XMLStreamException {
        xml.writeStartElement("title-info");
        writeNewline(xml);

        // Жанры
        for (String genre : info.genres()) {
            writeSimpleElement(xml, "genre", genre);
        }

        // Авторы
        for (Author author : info.authors()) {
            writeAuthor(xml, author);
        }

        // Название
        writeSimpleElement(xml, "book-title", info.bookTitle());

        // Аннотация
        if (info.annotation() != null && !info.annotation().isEmpty()) {
            xml.writeStartElement("annotation");
            writeNewline(xml);
            for (BlockElement block : info.annotation()) {
                writeBlock(xml, block);
            }
            xml.writeEndElement();
            writeNewline(xml);
        }

        // Язык
        writeSimpleElement(xml, "lang", info.lang());
        writeSimpleElement(xml, "src-lang", info.srcLang());

        // Серия
        if (info.sequence() != null) {
            xml.writeEmptyElement("sequence");
            xml.writeAttribute("name", info.sequence().name());
            if (info.sequence().number() != null) {
                xml.writeAttribute("number", String.valueOf(info.sequence().number()));
            }
            writeNewline(xml);
        }

        // Обложка
        if (info.coverImageIds() != null && !info.coverImageIds().isEmpty()) {
            xml.writeStartElement("coverpage");
            writeNewline(xml);
            for (String coverId : info.coverImageIds()) {
                xml.writeEmptyElement("image");
                xml.writeAttribute("l:href", "#" + coverId);
                writeNewline(xml);
            }
            xml.writeEndElement();
            writeNewline(xml);
        }

        xml.writeEndElement(); // </title-info>
        writeNewline(xml);
    }

    private void writeAuthor(XMLStreamWriter xml, Author author) throws XMLStreamException {
        xml.writeStartElement("author");
        writeNewline(xml);
        writeSimpleElement(xml, "first-name", author.firstName());
        writeSimpleElement(xml, "middle-name", author.middleName());
        writeSimpleElement(xml, "last-name", author.lastName());
        xml.writeEndElement();
        writeNewline(xml);
    }

    private void writeDocumentInfo(XMLStreamWriter xml, DocumentInfo info) throws XMLStreamException {
        xml.writeStartElement("document-info");
        writeNewline(xml);

        for (Author author : info.authors()) {
            writeAuthor(xml, author);
        }

        writeSimpleElement(xml, "program-used", info.programUsed());
        writeSimpleElement(xml, "date", info.date());
        writeSimpleElement(xml, "src-url", info.srcUrl());
        writeSimpleElement(xml, "src-ocr", info.srcOcr());
        writeSimpleElement(xml, "id", info.id());
        writeSimpleElement(xml, "version", info.version());

        if (info.history() != null && !info.history().isEmpty()) {
            xml.writeStartElement("history");
            writeNewline(xml);
            for (String line : info.history()) {
                writeSimpleElement(xml, "p", line);
            }
            xml.writeEndElement();
            writeNewline(xml);
        }

        xml.writeEndElement(); // </document-info>
        writeNewline(xml);
    }

    private void writePublishInfo(XMLStreamWriter xml, PublishInfo info) throws XMLStreamException {
        xml.writeStartElement("publish-info");
        writeNewline(xml);

        writeSimpleElement(xml, "book-name", info.bookName());
        writeSimpleElement(xml, "publisher", info.publisher());
        writeSimpleElement(xml, "city", info.city());
        writeSimpleElement(xml, "year", info.year());
        writeSimpleElement(xml, "isbn", info.isbn());

        xml.writeEndElement(); // </publish-info>
        writeNewline(xml);
    }

    // ========================================================================
    // ЗАПИСЬ <body>
    // ========================================================================

    private void writeBody(XMLStreamWriter xml, BodyDto body) throws XMLStreamException {
        xml.writeStartElement("body");
        if (body.name() != null && !body.name().isBlank()) {
            xml.writeAttribute("name", body.name());
        }
        writeNewline(xml);

        for (Section section : body.sections()) {
            writeSection(xml, section);
        }

        xml.writeEndElement(); // </body>
        writeNewline(xml);
    }

    private void writeSection(XMLStreamWriter xml, Section section) throws XMLStreamException {
        xml.writeStartElement("section");
        if (section.id() != null && !section.id().isBlank()) {
            xml.writeAttribute("id", section.id());
        }
        writeNewline(xml);

        // Заголовок
        if (section.title() != null && !section.title().isEmpty()) {
            xml.writeStartElement("title");
            writeNewline(xml);
            for (BlockElement block : section.title()) {
                writeBlock(xml, block);
            }
            xml.writeEndElement();
            writeNewline(xml);
        }

        // Содержимое
        if (section.content() != null) {
            for (BlockElement block : section.content()) {
                writeBlock(xml, block);
            }
        }

        // Вложенные секции
        if (section.subSections() != null) {
            for (Section sub : section.subSections()) {
                writeSection(xml, sub);
            }
        }

        xml.writeEndElement(); // </section>
        writeNewline(xml);
    }

    // ========================================================================
    // ЗАПИСЬ БЛОЧНЫХ ЭЛЕМЕНТОВ
    // ========================================================================

    private void writeBlock(XMLStreamWriter xml, BlockElement block) throws XMLStreamException {
        if (block == null) return;

        if (block instanceof Paragraph p) {
            writeParagraph(xml, p);
        } else if (block instanceof EmptyLine) {
            xml.writeEmptyElement("empty-line");
            writeNewline(xml);
        } else if (block instanceof Poem poem) {
            writePoem(xml, poem);
        } else if (block instanceof Table table) {
            writeTable(xml, table);
        } else if (block instanceof Cite cite) {
            writeCite(xml, cite);
        } else if (block instanceof Epigraph epigraph) {
            writeEpigraph(xml, epigraph);
        } else if (block instanceof Section section) {
            writeSection(xml, section);
        }
    }

    private void writeParagraph(XMLStreamWriter xml, Paragraph p) throws XMLStreamException {
        xml.writeStartElement("p");
        for (InlineElement inline : p.elements()) {
            writeInline(xml, inline);
        }
        xml.writeEndElement();
        writeNewline(xml);
    }

    private void writePoem(XMLStreamWriter xml, Poem poem) throws XMLStreamException {
        xml.writeStartElement("poem");
        if (poem.id() != null) {
            xml.writeAttribute("id", poem.id());
        }
        writeNewline(xml);

        // Заголовок стихотворения
        if (poem.title() != null && !poem.title().isEmpty()) {
            xml.writeStartElement("title");
            writeNewline(xml);
            for (BlockElement block : poem.title()) {
                writeBlock(xml, block);
            }
            xml.writeEndElement();
            writeNewline(xml);
        }

        // Эпиграф
        if (poem.epigraph() != null && !poem.epigraph().isEmpty()) {
            xml.writeStartElement("epigraph");
            writeNewline(xml);
            for (BlockElement block : poem.epigraph()) {
                writeBlock(xml, block);
            }
            xml.writeEndElement();
            writeNewline(xml);
        }

        // Строфы
        for (Stanza stanza : poem.stanzas()) {
            writeStanza(xml, stanza);
        }

        // Автор
        if (poem.author() != null && !poem.author().isBlank()) {
            writeSimpleElement(xml, "text-author", poem.author());
        }

        xml.writeEndElement(); // </poem>
        writeNewline(xml);
    }

    private void writeStanza(XMLStreamWriter xml, Stanza stanza) throws XMLStreamException {
        xml.writeStartElement("stanza");
        writeNewline(xml);

        for (Verse verse : stanza.verses()) {
            writeVerse(xml, verse);
        }

        xml.writeEndElement(); // </stanza>
        writeNewline(xml);
    }

    private void writeVerse(XMLStreamWriter xml, Verse verse) throws XMLStreamException {
        xml.writeStartElement("v");
        for (InlineElement inline : verse.elements()) {
            writeInline(xml, inline);
        }
        xml.writeEndElement();
        writeNewline(xml);
    }

    private void writeTable(XMLStreamWriter xml, Table table) throws XMLStreamException {
        xml.writeStartElement("table");
        writeNewline(xml);

        for (TableRow row : table.rows()) {
            writeTableRow(xml, row);
        }

        xml.writeEndElement(); // </table>
        writeNewline(xml);
    }

    private void writeTableRow(XMLStreamWriter xml, TableRow row) throws XMLStreamException {
        xml.writeStartElement("tr");
        writeNewline(xml);

        for (TableCell cell : row.cells()) {
            writeTableCell(xml, cell);
        }

        xml.writeEndElement(); // </tr>
        writeNewline(xml);
    }

    private void writeTableCell(XMLStreamWriter xml, TableCell cell) throws XMLStreamException {
        xml.writeStartElement("td");
        writeNewline(xml);

        for (BlockElement block : cell.content()) {
            writeBlock(xml, block);
        }

        xml.writeEndElement(); // </td>
        writeNewline(xml);
    }

    private void writeCite(XMLStreamWriter xml, Cite cite) throws XMLStreamException {
        xml.writeStartElement("cite");
        if (cite.id() != null) {
            xml.writeAttribute("id", cite.id());
        }
        writeNewline(xml);

        for (BlockElement block : cite.content()) {
            writeBlock(xml, block);
        }

        if (cite.author() != null && !cite.author().isBlank()) {
            writeSimpleElement(xml, "text-author", cite.author());
        }

        xml.writeEndElement(); // </cite>
        writeNewline(xml);
    }

    private void writeEpigraph(XMLStreamWriter xml, Epigraph epigraph) throws XMLStreamException {
        xml.writeStartElement("epigraph");
        if (epigraph.id() != null) {
            xml.writeAttribute("id", epigraph.id());
        }
        writeNewline(xml);

        for (BlockElement block : epigraph.content()) {
            writeBlock(xml, block);
        }

        if (epigraph.author() != null && !epigraph.author().isBlank()) {
            writeSimpleElement(xml, "text-author", epigraph.author());
        }

        xml.writeEndElement(); // </epigraph>
        writeNewline(xml);
    }

    // ========================================================================
    // ЗАПИСЬ ИНЛАЙН-ЭЛЕМЕНТОВ
    // ========================================================================

    private void writeInline(XMLStreamWriter xml, InlineElement inline) throws XMLStreamException {
        if (inline == null) return;

        if (inline instanceof Text t) {
            xml.writeCharacters(t.value());
        } else if (inline instanceof Strong s) {
            xml.writeStartElement("strong");
            for (InlineElement e : s.elements()) {
                writeInline(xml, e);
            }
            xml.writeEndElement();
        } else if (inline instanceof Emphasis e) {
            xml.writeStartElement("emphasis");
            for (InlineElement el : e.elements()) {
                writeInline(xml, el);
            }
            xml.writeEndElement();
        } else if (inline instanceof Strikethrough s) {
            xml.writeStartElement("strikethrough");
            for (InlineElement e : s.elements()) {
                writeInline(xml, e);
            }
            xml.writeEndElement();
        } else if (inline instanceof Sub s) {
            xml.writeStartElement("sub");
            for (InlineElement e : s.elements()) {
                writeInline(xml, e);
            }
            xml.writeEndElement();
        } else if (inline instanceof Sup s) {
            xml.writeStartElement("sup");
            for (InlineElement e : s.elements()) {
                writeInline(xml, e);
            }
            xml.writeEndElement();
        } else if (inline instanceof Link l) {
            writeLink(xml, l);
        } else if (inline instanceof ImageRef img) {
            writeImageRef(xml, img);
        }
    }

    private void writeLink(XMLStreamWriter xml, Link link) throws XMLStreamException {
        xml.writeStartElement("a");
        if (link.href() != null) {
            xml.writeAttribute("l:href", link.href());
        }
        if (link.type() != null && !link.type().isBlank()) {
            xml.writeAttribute("type", link.type());
        }
        for (InlineElement e : link.elements()) {
            writeInline(xml, e);
        }
        xml.writeEndElement();
    }

    private void writeImageRef(XMLStreamWriter xml, ImageRef img) throws XMLStreamException {
        xml.writeEmptyElement("image");
        if (img.href() != null) {
            xml.writeAttribute("l:href", img.href());
        }
        if (img.alt() != null && !img.alt().isBlank()) {
            xml.writeAttribute("alt", img.alt());
        }
    }

    // ========================================================================
    // ЗАПИСЬ <binary> (СТРИМИНГ BASE64)
    // ========================================================================

    private void writeBinary(XMLStreamWriter xml, Resource resource) throws XMLStreamException, IOException {
        xml.writeStartElement("binary");
        xml.writeAttribute("id", resource.id());
        xml.writeAttribute("content-type", resource.contentType());

        // Стримим base64 чанками, не загружая весь файл в память
        try (InputStream is = resource.dataProvider().getInputStream()) {
            byte[] buffer = new byte[BASE64_CHUNK_SIZE];
            int read;
            while ((read = is.read(buffer)) > 0) {
                // ✅ Если прочитан полный буфер — используем его напрямую.
                // Если неполный (последний чанк) — копируем нужную часть.
                byte[] toEncode = (read == buffer.length) ? buffer : Arrays.copyOf(buffer, read);
                String chunk = Base64.getEncoder().encodeToString(toEncode);
                xml.writeCharacters(chunk);
            }
        }

        xml.writeEndElement(); // </binary>
        writeNewline(xml);
    }

    // ========================================================================
    // УТИЛИТЫ
    // ========================================================================

    private void writeSimpleElement(XMLStreamWriter xml, String name, String value)
            throws XMLStreamException {
        if (value == null || value.isBlank()) return;
        xml.writeStartElement(name);
        xml.writeCharacters(value);
        xml.writeEndElement();
        writeNewline(xml);
    }

    private void writeNewline(XMLStreamWriter xml) throws XMLStreamException {
        if (prettyPrint) {
            xml.writeCharacters("\n");
        }
    }
}