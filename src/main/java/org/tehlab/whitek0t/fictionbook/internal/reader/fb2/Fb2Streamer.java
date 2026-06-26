package org.tehlab.whitek0t.fictionbook.internal.reader.fb2;

import org.tehlab.whitek0t.fictionbook.api.FictionBookStreamer;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.encoding.EncodingAwareInputStream;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.exception.InvalidFormatException;
import org.tehlab.whitek0t.fictionbook.internal.anchor.AnchorIndex;
import org.tehlab.whitek0t.fictionbook.internal.anchor.AnchorIndexBuilder;
import org.tehlab.whitek0t.fictionbook.internal.parser.jackson.DescriptionMapper;
import org.tehlab.whitek0t.fictionbook.internal.parser.stax.Fb2BlockParser;
import org.tehlab.whitek0t.fictionbook.internal.parser.stax.Fb2BodyParser;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Потоковая FB2-реализация {@link FictionBookStreamer}.
 *
 * <p><b>Гарантия:</b> <i>ленивые секции, eager-бинарники.</i> Главный выигрыш по
 * памяти на больших книгах — не держать всё дерево секций (весь текст) разом;
 * {@link #readNextSection()} отдаёт по одной секции верхнего уровня, не удерживая
 * предыдущие. Бинарники грузятся целиком (как и в {@code Fb2Reader} — ленивый seek
 * по исходному файлу слишком хрупок), но только по требованию {@link #getResource}.</p>
 *
 * <p><b>Ограничения v1:</b></p>
 * <ul>
 *   <li>Секции из {@code <body name="notes">} отдаются вперемешку с основными
 *       (в порядке документа): {@link Section} не несёт имени тела.</li>
 *   <li>{@link #buildAnchorIndex()} читает книгу целиком — это не потоковая операция.</li>
 * </ul>
 */
public final class Fb2Streamer implements FictionBookStreamer {

    private static final int BUFFER_SIZE = 8 * 1024;

    private final Path file;
    private final String fileName;
    private final XMLInputFactory staxFactory;
    private final Fb2BodyParser bodyParser;
    private final Fb2BlockParser blockParser;
    private final Fb2DescriptionReader descriptionReader;

    /** Поток и ридер для последовательного чтения description + секций. */
    private final InputStream sectionStream;
    private final XMLStreamReader xml;

    private Description description;
    private boolean descriptionConsumed;
    /** Ридер уже стоит на необработанном START-элементе (его не надо «листать» next-ом). */
    private boolean pendingStart;
    private boolean sectionsDone;

    /** Бинарники грузятся целиком по первому обращению ({@code null} = ещё не грузили). */
    private Map<String, Resource> resources;

    /**
     * Открывает FB2-файл для потокового чтения.
     *
     * @param file путь к файлу
     * @throws FictionBookException при ошибке открытия/инициализации парсера
     */
    public Fb2Streamer(Path file) throws FictionBookException {
        this.file = file;
        this.fileName = InvalidFormatException.extractFileName(file);
        this.staxFactory = newStaxFactory();
        this.blockParser = new Fb2BlockParser(staxFactory);
        this.bodyParser = new Fb2BodyParser(blockParser);
        this.descriptionReader = new Fb2DescriptionReader(staxFactory, new DescriptionMapper(blockParser));
        try {
            this.sectionStream = new BufferedInputStream(new EncodingAwareInputStream(file), BUFFER_SIZE);
            this.xml = staxFactory.createXMLStreamReader(sectionStream, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new FictionBookException("Failed to open FB2 for streaming: " + file, e);
        }
    }

    private static XMLInputFactory newStaxFactory() {
        XMLInputFactory f = XMLInputFactory.newInstance();
        f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_COALESCING, false);
        return f;
    }

    @Override
    public Description readDescription() throws FictionBookException {
        ensureDescriptionConsumed();
        return description;
    }

    @Override
    public Section readNextSection() throws FictionBookException {
        ensureDescriptionConsumed();
        if (sectionsDone) {
            return null;
        }
        try {
            while (true) {
                int event;
                if (pendingStart) {
                    pendingStart = false;
                    event = XMLStreamConstants.START_ELEMENT; // ридер уже стоит на START
                } else {
                    if (!xml.hasNext()) {
                        sectionsDone = true;
                        return null;
                    }
                    event = xml.next();
                }

                if (event != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                switch (xml.getLocalName()) {
                    case "section" -> {
                        return bodyParser.parseSection(xml, fileName);
                    }
                    case "binary" -> {
                        // Дошли до бинарников — секции кончились.
                        sectionsDone = true;
                        return null;
                    }
                    case "body", "FictionBook" -> {
                        // Спускаемся внутрь — продолжаем цикл.
                    }
                    default ->
                        // Элементы уровня тела, не являющиеся секцией (title/epigraph/
                        // image и т.п.), пропускаем целиком.
                            blockParser.skipUnknownElement(xml);
                }
            }
        } catch (XMLStreamException e) {
            throw new FictionBookException("XML parsing error while streaming sections in " + fileName, e);
        }
    }

    @Override
    public Resource getResource(String id) throws FictionBookException {
        if (id == null) {
            return null;
        }
        if (resources == null) {
            resources = loadAllBinaries();
        }
        String key = id.startsWith("#") ? id.substring(1) : id;
        return resources.get(key);
    }

    @Override
    public AnchorIndex buildAnchorIndex() throws FictionBookException {
        // Индекс якорей по своей природе охватывает всю книгу — читаем её целиком.
        return AnchorIndexBuilder.fromDto(new Fb2Reader().read(file));
    }

    @Override
    public void close() throws Exception {
        try {
            xml.close();
        } finally {
            sectionStream.close();
        }
    }

    // ========================================================================
    // ВНУТРЕННЕЕ
    // ========================================================================

    /**
     * Прокручивает ридер до конца {@code <description>} (разобрав и закэшировав его).
     * Если description отсутствует, останавливается на первом {@code <body>}/{@code <binary>}
     * и выставляет {@link #pendingStart}, чтобы потоковый цикл секций обработал этот START.
     */
    private void ensureDescriptionConsumed() throws FictionBookException {
        if (descriptionConsumed) {
            return;
        }
        descriptionConsumed = true;
        try {
            while (xml.hasNext()) {
                if (xml.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                String tag = xml.getLocalName();
                if ("description".equals(tag)) {
                    description = descriptionReader.read(xml, fileName);
                    return;
                }
                if ("body".equals(tag) || "binary".equals(tag)) {
                    pendingStart = true; // description нет — ридер стоит на этом START
                    return;
                }
                // FictionBook (корень) и прочее — спускаемся/пропускаем, продолжая цикл.
            }
        } catch (XMLStreamException e) {
            throw new FictionBookException("XML parsing error while reading <description> in " + fileName, e);
        }
    }

    /** Отдельным проходом грузит все {@code <binary>} (они в конце FB2-файла). */
    private Map<String, Resource> loadAllBinaries() throws FictionBookException {
        Map<String, Resource> map = new LinkedHashMap<>();
        try (InputStream raw = new EncodingAwareInputStream(file);
             BufferedInputStream buffered = new BufferedInputStream(raw, BUFFER_SIZE)) {

            XMLStreamReader r = staxFactory.createXMLStreamReader(buffered, StandardCharsets.UTF_8.name());
            try {
                while (r.hasNext()) {
                    if (r.next() == XMLStreamConstants.START_ELEMENT && "binary".equals(r.getLocalName())) {
                        Resource res = Fb2BinaryReader.readBinary(r, fileName);
                        if (res != null) {
                            map.put(res.id(), res);
                        }
                    }
                }
            } finally {
                r.close();
            }
        } catch (FictionBookException e) {
            throw e;
        } catch (Exception e) {
            throw new FictionBookException("Failed to load binaries from " + file, e);
        }
        return map;
    }
}
