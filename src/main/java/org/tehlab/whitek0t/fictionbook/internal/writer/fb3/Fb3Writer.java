package org.tehlab.whitek0t.fictionbook.internal.writer.fb3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.*;
import org.tehlab.whitek0t.fictionbook.dto.description.*;
import org.tehlab.whitek0t.fictionbook.dto.inline.*;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.internal.sanitizer.SanitizerPipeline;
import org.tehlab.whitek0t.fictionbook.util.MimeTypeResolver;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Записывает {@link FictionBookDto} в FB3-файл (OPC/ZIP-контейнер).
 *
 * <p>Зеркально к {@code Fb3Reader}: книга раскладывается на части пакета —
 * {@code [Content_Types].xml}, {@code _rels/.rels}, {@code fb3/description.xml},
 * {@code fb3/body.xml} (+ {@code notes.xml} для сносок) и {@code fb3/img/*} для
 * картинок, — связанные OPC-связями {@code _rels/*.rels}.</p>
 *
 * <p>Особенности:</p>
 * <ul>
 *   <li><b>Автосанитизация:</b> перед записью применяется {@link SanitizerPipeline}
 *       (как и в FB2-райтере).</li>
 *   <li><b>Картинки — сырыми байтами</b> (в отличие от base64 в FB2): каждый
 *       {@link Resource} становится частью {@code fb3/img/<id>} и связью типа image.</li>
 *   <li><b>Ссылки {@code <img>}</b> в теле пишутся как {@code l:href="rId…"} —
 *       Id OPC-связи, указывающей на нужную часть-картинку.</li>
 *   <li><b>FB3-теги:</b> {@code <img>} вместо {@code <image>}, {@code <blockquote>}
 *       вместо {@code <cite>}; остальные блочные/инлайновые теги совпадают с FB2.</li>
 * </ul>
 *
 * <p>Записанный пакет читается обратно {@code Fb3Reader} с сохранением метаданных,
 * структуры тела и картинок.</p>
 */
public class Fb3Writer {

    private static final Logger log = LoggerFactory.getLogger(Fb3Writer.class);

    private static final String NS_DESCRIPTION = "http://www.fictionbook.org/FictionBook3/description";
    private static final String NS_BODY = "http://www.fictionbook.org/FictionBook3/body";
    private static final String NS_XLINK = "http://www.w3.org/1999/xlink";
    private static final String NS_CONTENT_TYPES = "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String NS_RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships";

    private static final String REL_BOOK = "http://www.fictionbook.org/FictionBook3/relationships/Book";
    private static final String REL_BODY = "http://www.fictionbook.org/FictionBook3/relationships/body";
    private static final String REL_NOTES = "http://www.fictionbook.org/FictionBook3/relationships/notes";
    private static final String REL_IMAGE = "http://www.fictionbook.org/FictionBook3/relationships/image";
    private static final String REL_COVER = "http://www.fictionbook.org/FictionBook3/relationships/cover";

    private static final String CT_DESCRIPTION = "application/fb3-description+xml";
    private static final String CT_BODY = "application/fb3-body+xml";

    private static final XMLOutputFactory XML_FACTORY = XMLOutputFactory.newInstance();

    private SanitizerPipeline sanitizerPipeline = SanitizerPipeline.standard();
    private boolean prettyPrint = true;
    private LocalDateTime entryTime = null;

    /**
     * Устанавливает пайплайн санитайзеров ({@code null} отключает санитизацию).
     *
     * @param pipeline пайплайн или {@code null}
     */
    public void setSanitizerPipeline(SanitizerPipeline pipeline) {
        this.sanitizerPipeline = pipeline;
    }

    /**
     * Включает/выключает форматирование XML-частей с отступами.
     *
     * @param prettyPrint {@code true} — переносы строк между элементами
     */
    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /**
     * Задаёт фиксированную метку времени для всех записей ZIP-контейнера.
     * <p>
     * По умолчанию ({@code null}) записи получают <b>текущее</b> время — обычное
     * поведение для прода. Фиксированное значение делает вывод FB3 детерминированным
     * (воспроизводимым байт-в-байт), что нужно, например, в round-trip тестах или для
     * reproducible-сборок.
     *
     * @param entryTime метка времени записей или {@code null} для текущего времени
     */
    public void setEntryTime(LocalDateTime entryTime) {
        this.entryTime = entryTime;
    }

    /**
     * Записывает книгу в FB3-файл по указанному пути.
     *
     * @param book        DTO книги
     * @param destination путь к выходному {@code .fb3}-файлу
     * @throws FictionBookException при ошибке записи
     */
    public void write(FictionBookDto book, Path destination) throws FictionBookException {
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(destination))) {
            write(book, os);
        } catch (FictionBookException e) {
            throw e;
        } catch (Exception e) {
            throw new FictionBookException("Failed to write FB3: " + destination, e);
        }
    }

    /**
     * Записывает книгу в поток как FB3-архив.
     *
     * @param book         DTO книги
     * @param outputStream поток назначения
     * @throws FictionBookException при ошибке записи
     */
    public void write(FictionBookDto book, OutputStream outputStream) throws FictionBookException {
        try {
            FictionBookDto clean = sanitize(book);
            writeContainer(clean, outputStream);
        } catch (Exception e) {
            throw new FictionBookException("Failed to write FB3 to stream", e);
        }
    }

    // ========================================================================
    // КОНТЕЙНЕР
    // ========================================================================

    private FictionBookDto sanitize(FictionBookDto book) {
        if (sanitizerPipeline == null) {
            return book;
        }
        return sanitizerPipeline.sanitize(book);
    }

    private void writeContainer(FictionBookDto book, OutputStream outputStream)
            throws XMLStreamException, IOException {

        // 1. Раскладка картинок: id ресурса → часть пакета и Id связи.
        List<ImagePart> images = planImages(book);
        Map<String, ImagePart> byResourceId = new LinkedHashMap<>();
        for (ImagePart img : images) {
            byResourceId.put(img.resourceId, img);
        }

        // 2. Раскладка тел: основное → body.xml, сноски → notes.xml.
        List<BodyPart> bodyParts = planBodies(book);

        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            putEntry(zip, "[Content_Types].xml", contentTypesXml(images, bodyParts));
            putEntry(zip, "_rels/.rels", rootRelsXml());

            putEntry(zip, "fb3/description.xml",
                    descriptionXml(book.description()));
            putEntry(zip, "fb3/_rels/description.xml.rels",
                    descriptionRelsXml(bodyParts, book.description(), byResourceId));

            for (BodyPart bp : bodyParts) {
                putEntry(zip, "fb3/" + bp.fileName, bodyXml(bp.body, byResourceId));
                putEntry(zip, "fb3/_rels/" + bp.fileName + ".rels", bodyRelsXml(images));
            }

            for (ImagePart img : images) {
                writeImageEntry(zip, img);
            }
        }
    }

    // ========================================================================
    // ПЛАНИРОВАНИЕ ЧАСТЕЙ
    // ========================================================================

    private List<ImagePart> planImages(FictionBookDto book) {
        List<ImagePart> images = new ArrayList<>();
        int n = 1;
        for (Resource resource : book.resources().values()) {
            String ext = extensionFor(resource.id(), resource.contentType());
            String fileName = sanitizeFileName(resource.id(), ext);
            images.add(new ImagePart(
                    resource.id(),
                    "fb3/img/" + fileName,
                    "img/" + fileName,
                    "rId" + n++,
                    ext,
                    resource
            ));
        }
        return images;
    }

    private List<BodyPart> planBodies(FictionBookDto book) {
        List<BodyPart> parts = new ArrayList<>();
        boolean mainTaken = false;
        int extra = 2;
        for (BodyDto body : book.bodies()) {
            boolean isNotes = "notes".equalsIgnoreCase(body.name());
            String fileName;
            String relType;
            if (isNotes) {
                fileName = "notes.xml";
                relType = REL_NOTES;
            } else if (!mainTaken) {
                fileName = "body.xml";
                relType = REL_BODY;
                mainTaken = true;
            } else {
                fileName = "body" + (extra++) + ".xml";
                relType = REL_BODY;
            }
            parts.add(new BodyPart(body, fileName, relType));
        }
        return parts;
    }

    // ========================================================================
    // [Content_Types].xml
    // ========================================================================

    private byte[] contentTypesXml(List<ImagePart> images, List<BodyPart> bodyParts)
            throws XMLStreamException {
        // Default-типы по расширениям картинок (дедуплицируем).
        Map<String, String> extToType = new LinkedHashMap<>();
        for (ImagePart img : images) {
            String ext = img.ext.startsWith(".") ? img.ext.substring(1) : img.ext;
            extToType.putIfAbsent(ext.toLowerCase(), img.resource.contentType());
        }
        return toXml(xml -> {
            xml.writeStartElement("Types");
            xml.writeDefaultNamespace(NS_CONTENT_TYPES);
            nl(xml);

            writeDefault(xml, "rels", "application/vnd.openxmlformats-package.relationships+xml");
            writeDefault(xml, "xml", "application/xml");
            for (Map.Entry<String, String> e : extToType.entrySet()) {
                writeDefault(xml, e.getKey(), e.getValue());
            }

            writeOverride(xml, "/fb3/description.xml", CT_DESCRIPTION);
            for (BodyPart bp : bodyParts) {
                writeOverride(xml, "/fb3/" + bp.fileName, CT_BODY);
            }

            xml.writeEndElement(); // Types
            nl(xml);
        });
    }

    private void writeDefault(XMLStreamWriter xml, String ext, String contentType)
            throws XMLStreamException {
        xml.writeEmptyElement("Default");
        xml.writeAttribute("Extension", ext);
        xml.writeAttribute("ContentType", contentType == null ? "application/octet-stream" : contentType);
        nl(xml);
    }

    private void writeOverride(XMLStreamWriter xml, String partName, String contentType)
            throws XMLStreamException {
        xml.writeEmptyElement("Override");
        xml.writeAttribute("PartName", partName);
        xml.writeAttribute("ContentType", contentType);
        nl(xml);
    }

    // ========================================================================
    // _rels/.rels  и  description.xml.rels  и  body.xml.rels
    // ========================================================================

    private byte[] rootRelsXml() throws XMLStreamException {
        return toXml(xml -> {
            xml.writeStartElement("Relationships");
            xml.writeDefaultNamespace(NS_RELATIONSHIPS);
            nl(xml);
            writeRelationship(xml, "rId1", REL_BOOK, "fb3/description.xml");
            xml.writeEndElement();
            nl(xml);
        });
    }

    private byte[] descriptionRelsXml(List<BodyPart> bodyParts, Description description,
                                      Map<String, ImagePart> byResourceId) throws XMLStreamException {
        return toXml(xml -> {
            xml.writeStartElement("Relationships");
            xml.writeDefaultNamespace(NS_RELATIONSHIPS);
            nl(xml);

            int n = 1;
            // Связи на тела (body.xml относительно каталога fb3/ описания).
            for (BodyPart bp : bodyParts) {
                writeRelationship(xml, "rId" + n++, bp.relType, bp.fileName);
            }
            // Связи на обложки.
            for (String coverId : coverIds(description)) {
                ImagePart img = byResourceId.get(coverId);
                if (img != null) {
                    writeRelationship(xml, "rId" + n++, REL_COVER, img.target);
                }
            }

            xml.writeEndElement();
            nl(xml);
        });
    }

    private byte[] bodyRelsXml(List<ImagePart> images) throws XMLStreamException {
        return toXml(xml -> {
            xml.writeStartElement("Relationships");
            xml.writeDefaultNamespace(NS_RELATIONSHIPS);
            nl(xml);
            for (ImagePart img : images) {
                writeRelationship(xml, img.relId, REL_IMAGE, img.target);
            }
            xml.writeEndElement();
            nl(xml);
        });
    }

    private void writeRelationship(XMLStreamWriter xml, String id, String type, String target)
            throws XMLStreamException {
        xml.writeEmptyElement("Relationship");
        xml.writeAttribute("Id", id);
        xml.writeAttribute("Type", type);
        xml.writeAttribute("Target", target);
        nl(xml);
    }

    // ========================================================================
    // description.xml
    // ========================================================================

    private byte[] descriptionXml(Description desc) throws XMLStreamException {
        return toXml(xml -> {
            xml.writeStartElement("fb3-description");
            xml.writeDefaultNamespace(NS_DESCRIPTION);

            TitleInfo ti = desc == null ? null : desc.titleInfo();
            DocumentInfo di = desc == null ? null : desc.documentInfo();

            if (di != null && di.id() != null) {
                xml.writeAttribute("id", di.id());
            }
            if (di != null && di.version() != null) {
                xml.writeAttribute("version", di.version());
            }
            nl(xml);

            if (ti != null) {
                // Название
                xml.writeStartElement("title");
                nl(xml);
                writeTextElement(xml, "main", ti.bookTitle() == null ? "" : ti.bookTitle());
                xml.writeEndElement(); // title
                nl(xml);

                // Авторы
                if (ti.authors() != null && !ti.authors().isEmpty()) {
                    xml.writeStartElement("fb3-relations");
                    nl(xml);
                    for (Author a : ti.authors()) {
                        writeAuthorSubject(xml, a);
                    }
                    xml.writeEndElement(); // fb3-relations
                    nl(xml);
                }

                // Жанры
                if (ti.genres() != null && !ti.genres().isEmpty()) {
                    xml.writeStartElement("fb3-classification");
                    nl(xml);
                    for (String genre : ti.genres()) {
                        writeTextElement(xml, "subject", genre);
                    }
                    xml.writeEndElement(); // fb3-classification
                    nl(xml);
                }

                writeTextElement(xml, "lang", ti.lang());

                if (ti.sequence() != null && ti.sequence().name() != null) {
                    xml.writeEmptyElement("sequence");
                    xml.writeAttribute("name", ti.sequence().name());
                    if (ti.sequence().number() != null) {
                        xml.writeAttribute("number", String.valueOf(ti.sequence().number()));
                    }
                    nl(xml);
                }

                if (ti.annotation() != null && !ti.annotation().isEmpty()) {
                    xml.writeStartElement("annotation");
                    nl(xml);
                    for (BlockElement block : ti.annotation()) {
                        writeBlock(xml, block, null);
                    }
                    xml.writeEndElement(); // annotation
                    nl(xml);
                }
            }

            xml.writeEndElement(); // fb3-description
            nl(xml);
        });
    }

    private void writeAuthorSubject(XMLStreamWriter xml, Author a) throws XMLStreamException {
        xml.writeStartElement("subject");
        xml.writeAttribute("link", "author");
        nl(xml);
        writeTextElement(xml, "first-name", a.firstName());
        writeTextElement(xml, "middle-name", a.middleName());
        writeTextElement(xml, "last-name", a.lastName());
        xml.writeEndElement(); // subject
        nl(xml);
    }

    // ========================================================================
    // body.xml
    // ========================================================================

    private byte[] bodyXml(BodyDto body, Map<String, ImagePart> byResourceId)
            throws XMLStreamException {
        return toXml(xml -> {
            xml.writeStartElement("fb3-body");
            xml.writeDefaultNamespace(NS_BODY);
            xml.writeNamespace("l", NS_XLINK);
            nl(xml);

            for (Section section : body.sections()) {
                writeSection(xml, section, byResourceId);
            }

            xml.writeEndElement(); // fb3-body
            nl(xml);
        });
    }

    private void writeSection(XMLStreamWriter xml, Section section, Map<String, ImagePart> images)
            throws XMLStreamException {
        xml.writeStartElement("section");
        if (section.id() != null && !section.id().isBlank()) {
            xml.writeAttribute("id", section.id());
        }
        nl(xml);

        if (section.title() != null && !section.title().isEmpty()) {
            xml.writeStartElement("title");
            nl(xml);
            for (BlockElement block : section.title()) {
                writeBlock(xml, block, images);
            }
            xml.writeEndElement(); // title
            nl(xml);
        }

        if (section.content() != null) {
            for (BlockElement block : section.content()) {
                writeBlock(xml, block, images);
            }
        }

        if (section.subSections() != null) {
            for (Section sub : section.subSections()) {
                writeSection(xml, sub, images);
            }
        }

        xml.writeEndElement(); // section
        nl(xml);
    }

    // ========================================================================
    // БЛОЧНЫЕ ЭЛЕМЕНТЫ
    // ========================================================================

    private void writeBlock(XMLStreamWriter xml, BlockElement block, Map<String, ImagePart> images)
            throws XMLStreamException {
        switch (block) {
            case null -> {
                return;
            }
            case Paragraph p -> writeContainerTag(xml, "p", p.elements(), images);
            case EmptyLine emptyLine -> {
                xml.writeEmptyElement("empty-line");
                nl(xml);
            }
            case Poem poem -> writePoem(xml, poem, images);
            case Table table -> writeTable(xml, table, images);
            case Cite cite -> writeCite(xml, cite, images);
            case Epigraph epigraph -> writeEpigraph(xml, epigraph, images);
            case BlockImage image -> writeBlockImage(xml, image, images);
            case Section section -> writeSection(xml, section, images);
            default -> {
            }
        }

    }

    /** Записывает блочную картинку как FB3 {@code <img>} с relId OPC-связи. */
    private void writeBlockImage(XMLStreamWriter xml, BlockImage img, Map<String, ImagePart> images)
            throws XMLStreamException {
        xml.writeEmptyElement("img");
        String relId = resolveImageRel(img.href(), images);
        if (relId != null) {
            xml.writeAttribute("l:href", relId);
        } else if (img.href() != null) {
            xml.writeAttribute("l:href", img.href());
        }
        if (img.alt() != null && !img.alt().isBlank()) {
            xml.writeAttribute("alt", img.alt());
        }
        nl(xml);
    }

    /** Записывает простой контейнер с инлайн-содержимым ({@code <p>}, {@code <v>}, {@code <subtitle>}). */
    private void writeContainerTag(XMLStreamWriter xml, String tag, List<InlineElement> elements,
                                   Map<String, ImagePart> images) throws XMLStreamException {
        xml.writeStartElement(tag);
        if (elements != null) {
            for (InlineElement inline : elements) {
                writeInline(xml, inline, images);
            }
        }
        xml.writeEndElement();
        nl(xml);
    }

    private void writePoem(XMLStreamWriter xml, Poem poem, Map<String, ImagePart> images)
            throws XMLStreamException {
        xml.writeStartElement("poem");
        if (poem.id() != null) {
            xml.writeAttribute("id", poem.id());
        }
        nl(xml);

        if (poem.title() != null && !poem.title().isEmpty()) {
            xml.writeStartElement("title");
            nl(xml);
            for (BlockElement block : poem.title()) {
                writeBlock(xml, block, images);
            }
            xml.writeEndElement();
            nl(xml);
        }

        if (poem.epigraph() != null && !poem.epigraph().isEmpty()) {
            xml.writeStartElement("epigraph");
            nl(xml);
            for (BlockElement block : poem.epigraph()) {
                writeBlock(xml, block, images);
            }
            xml.writeEndElement();
            nl(xml);
        }

        for (Stanza stanza : poem.stanzas()) {
            xml.writeStartElement("stanza");
            nl(xml);
            for (Verse verse : stanza.verses()) {
                writeContainerTag(xml, "v", verse.elements(), images);
            }
            xml.writeEndElement(); // stanza
            nl(xml);
        }

        if (poem.author() != null && !poem.author().isBlank()) {
            writeTextElement(xml, "text-author", poem.author());
        }

        xml.writeEndElement(); // poem
        nl(xml);
    }

    private void writeTable(XMLStreamWriter xml, Table table, Map<String, ImagePart> images)
            throws XMLStreamException {
        xml.writeStartElement("table");
        nl(xml);
        for (TableRow row : table.rows()) {
            xml.writeStartElement("tr");
            nl(xml);
            for (TableCell cell : row.cells()) {
                xml.writeStartElement("td");
                nl(xml);
                for (BlockElement block : cell.content()) {
                    writeBlock(xml, block, images);
                }
                xml.writeEndElement(); // td
                nl(xml);
            }
            xml.writeEndElement(); // tr
            nl(xml);
        }
        xml.writeEndElement(); // table
        nl(xml);
    }

    /** FB3 пишет цитату как {@code <blockquote>} (FB2-{@code <cite>}). */
    private void writeCite(XMLStreamWriter xml, Cite cite, Map<String, ImagePart> images)
            throws XMLStreamException {
        xml.writeStartElement("blockquote");
        if (cite.id() != null) {
            xml.writeAttribute("id", cite.id());
        }
        nl(xml);
        for (BlockElement block : cite.content()) {
            writeBlock(xml, block, images);
        }
        if (cite.author() != null && !cite.author().isBlank()) {
            writeTextElement(xml, "text-author", cite.author());
        }
        xml.writeEndElement(); // blockquote
        nl(xml);
    }

    private void writeEpigraph(XMLStreamWriter xml, Epigraph epigraph, Map<String, ImagePart> images)
            throws XMLStreamException {
        xml.writeStartElement("epigraph");
        if (epigraph.id() != null) {
            xml.writeAttribute("id", epigraph.id());
        }
        nl(xml);
        for (BlockElement block : epigraph.content()) {
            writeBlock(xml, block, images);
        }
        if (epigraph.author() != null && !epigraph.author().isBlank()) {
            writeTextElement(xml, "text-author", epigraph.author());
        }
        xml.writeEndElement(); // epigraph
        nl(xml);
    }

    // ========================================================================
    // ИНЛАЙН-ЭЛЕМЕНТЫ
    // ========================================================================

    private void writeInline(XMLStreamWriter xml, InlineElement inline, Map<String, ImagePart> images)
            throws XMLStreamException {
        switch (inline) {
            case null -> {
                return;
            }
            case Text t -> xml.writeCharacters(t.value());
            case Strong s -> writeInlineContainer(xml, "strong", s.elements(), images);
            case Emphasis e -> writeInlineContainer(xml, "emphasis", e.elements(), images);
            case Strikethrough s -> writeInlineContainer(xml, "strikethrough", s.elements(), images);
            case Sub s -> writeInlineContainer(xml, "sub", s.elements(), images);
            case Sup s -> writeInlineContainer(xml, "sup", s.elements(), images);
            case Link l -> {
                xml.writeStartElement("a");
                if (l.href() != null) {
                    xml.writeAttribute("l:href", l.href());
                }
                if (l.type() != null && !l.type().isBlank()) {
                    xml.writeAttribute("type", l.type());
                }
                for (InlineElement e : l.elements()) {
                    writeInline(xml, e, images);
                }
                xml.writeEndElement();
            }
            case ImageRef img -> writeImageRef(xml, img, images);
            default -> {
            }
        }

    }

    private void writeInlineContainer(XMLStreamWriter xml, String tag, List<InlineElement> elements,
                                      Map<String, ImagePart> images) throws XMLStreamException {
        xml.writeStartElement(tag);
        for (InlineElement e : elements) {
            writeInline(xml, e, images);
        }
        xml.writeEndElement();
    }

    private void writeImageRef(XMLStreamWriter xml, ImageRef img, Map<String, ImagePart> images)
            throws XMLStreamException {
        xml.writeEmptyElement("img");
        String relId = resolveImageRel(img.href(), images);
        if (relId != null) {
            xml.writeAttribute("l:href", relId);
        } else if (img.href() != null) {
            xml.writeAttribute("l:href", img.href());
        }
        if (img.alt() != null && !img.alt().isBlank()) {
            xml.writeAttribute("alt", img.alt());
        }
    }

    /** Сопоставляет ссылку {@code "#id"} картинки с Id OPC-связи; {@code null} — если не найдена. */
    private String resolveImageRel(String href, Map<String, ImagePart> images) {
        if (href == null || images == null) return null;
        String id = href.startsWith("#") ? href.substring(1) : href;
        ImagePart img = images.get(id);
        return img != null ? img.relId : null;
    }

    // ========================================================================
    // КАРТИНКИ-ЧАСТИ
    // ========================================================================

    private void writeImageEntry(ZipOutputStream zip, ImagePart img) throws IOException {
        zip.putNextEntry(entry(img.partName));
        try (InputStream is = img.resource.dataProvider().getInputStream()) {
            is.transferTo(zip);
        }
        zip.closeEntry();
    }

    // ========================================================================
    // УТИЛИТЫ
    // ========================================================================

    private List<String> coverIds(Description desc) {
        if (desc == null || desc.titleInfo() == null || desc.titleInfo().coverImageIds() == null) {
            return List.of();
        }
        return desc.titleInfo().coverImageIds();
    }

    /** Расширение (с точкой) для части-картинки: из id, иначе по content-type. */
    private String extensionFor(String resourceId, String contentType) {
        int dot = resourceId.lastIndexOf('.');
        if (dot > 0 && dot < resourceId.length() - 1) {
            return resourceId.substring(dot);
        }
        return MimeTypeResolver.toExtension(contentType);
    }

    /** Безопасное имя файла части-картинки: id с гарантированным расширением. */
    private String sanitizeFileName(String resourceId, String ext) {
        String base = resourceId.replaceAll("[\\\\/\\s]+", "_");
        if (!base.toLowerCase().endsWith(ext.toLowerCase())) {
            base = base + ext;
        }
        return base;
    }

    private void putEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(entry(name));
        zip.write(data);
        zip.closeEntry();
    }

    /**
     * Создаёт {@link ZipEntry}. Если задана {@link #setEntryTime(LocalDateTime)} метка
     * времени — проставляет её (детерминированный вывод); иначе запись получает текущее
     * время (поведение по умолчанию).
     */
    private ZipEntry entry(String name) {
        ZipEntry e = new ZipEntry(name);
        if (entryTime != null) {
            e.setTimeLocal(entryTime);
        }
        return e;
    }

    private void writeTextElement(XMLStreamWriter xml, String name, String value)
            throws XMLStreamException {
        if (value == null || value.isBlank()) return;
        xml.writeStartElement(name);
        xml.writeCharacters(value);
        xml.writeEndElement();
        nl(xml);
    }

    private void nl(XMLStreamWriter xml) throws XMLStreamException {
        if (prettyPrint) {
            xml.writeCharacters("\n");
        }
    }

    /** Сериализует один XML-фрагмент пакета в байты (UTF-8, с XML-декларацией). */
    private byte[] toXml(XmlBody body) throws XMLStreamException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        XMLStreamWriter xml = XML_FACTORY.createXMLStreamWriter(writer);
        xml.writeStartDocument("UTF-8", "1.0");
        nl(xml);
        body.write(xml);
        xml.writeEndDocument();
        xml.flush();
        xml.close();
        try {
            writer.flush();
        } catch (IOException e) {
            throw new XMLStreamException("Failed to flush XML part", e);
        }
        return baos.toByteArray();
    }

    /** Тело генератора XML-части (для {@link #toXml}). */
    @FunctionalInterface
    private interface XmlBody {
        void write(XMLStreamWriter xml) throws XMLStreamException;
    }

    /** Спланированная часть-картинка: ресурс → имя части, цель и Id связи. */
    private static final class ImagePart {
        final String resourceId;
        final String partName;   // fb3/img/<file>
        final String target;     // img/<file> (относительно fb3/)
        final String relId;      // rIdN
        final String ext;        // ".jpg"
        final Resource resource;

        ImagePart(String resourceId, String partName, String target, String relId,
                  String ext, Resource resource) {
            this.resourceId = resourceId;
            this.partName = partName;
            this.target = target;
            this.relId = relId;
            this.ext = ext;
            this.resource = resource;
        }
    }

    /** Спланированная часть-тело: тело → имя части и тип OPC-связи. */
    private static final class BodyPart {
        final BodyDto body;
        final String fileName;   // body.xml / notes.xml
        final String relType;

        BodyPart(BodyDto body, String fileName, String relType) {
            this.body = body;
            this.fileName = fileName;
            this.relType = relType;
        }
    }
}
