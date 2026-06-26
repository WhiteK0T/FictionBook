package org.tehlab.whitek0t.fictionbook.internal.reader.fb2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.encoding.EncodingAwareInputStream;
import org.tehlab.whitek0t.fictionbook.encoding.EncodingDetector;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.exception.InvalidFormatException;
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
import java.util.*;

/**
 * Читает FB2-файл в {@link FictionBookDto}.
 *
 * <p>Особенности реализации:</p>
 * <ul>
 *   <li><b>Один проход:</b> файл читается один раз StAX-ридером</li>
 *   <li><b>Гибридный парсинг:</b> {@code <description>} парсится Jackson-ом,
 *       {@code <body>} — стеком билдеров через {@link Fb2BodyParser}</li>
 *   <li><b>Eager-загрузка бинарников:</b> {@code <binary>} читается сразу в память
 *       (ленивое чтение с seek требует сложной синхронизации кодировок и буферов)</li>
 *   <li><b>Автоопределение кодировки</b> через {@link EncodingDetector}</li>
 *   <li><b>Прощающий режим:</b> неизвестные теги пропускаются, не ломая парсинг</li>
 * </ul>
 */
public class Fb2Reader {

    private static final Logger log = LoggerFactory.getLogger(Fb2Reader.class);

    private static final int BUFFER_SIZE = 8 * 1024;

    private final XMLInputFactory staxFactory;

    public Fb2Reader() {
        this.staxFactory = XMLInputFactory.newInstance();
        staxFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        staxFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        staxFactory.setProperty(XMLInputFactory.IS_COALESCING, false);
    }

    /**
     * Читает FB2-файл по указанному пути.
     */
    public FictionBookDto read(Path file) throws FictionBookException {
        String fileName = InvalidFormatException.extractFileName(file);

        try (InputStream raw = new EncodingAwareInputStream(file);
             BufferedInputStream buffered = new BufferedInputStream(raw, BUFFER_SIZE)) {

            return parseStream(buffered, file, fileName);

        } catch (FictionBookException e) {
            throw e;
        } catch (Exception e) {
            throw new FictionBookException("Failed to read FB2: " + file, e);
        }
    }

    /**
     * Читает FB2 из InputStream (для стримера / тестов).
     */
    public FictionBookDto read(InputStream inputStream) throws FictionBookException {
        try {
            BufferedInputStream buffered = new BufferedInputStream(inputStream, BUFFER_SIZE);
            return parseStream(buffered, null, "<stream>");
        } catch (FictionBookException e) {
            throw e;
        } catch (Exception e) {
            throw new FictionBookException("Failed to read FB2 from stream", e);
        }
    }

    // ========================================================================
    // ОСНОВНОЙ ЦИКЛ ПАРСИНГА
    // ========================================================================

    private FictionBookDto parseStream(BufferedInputStream buffered,
                                       Path sourceFile,
                                       String fileName) throws FictionBookException {

        try {
            // EncodingAwareInputStream уже перекодировал поток в UTF-8
            XMLStreamReader xml = staxFactory.createXMLStreamReader(buffered, StandardCharsets.UTF_8.name());

            Description description = null;
            List<BodyDto> bodies = new ArrayList<>();
            Map<String, Resource> resources = new LinkedHashMap<>();

            Fb2BlockParser blockParser = new Fb2BlockParser(staxFactory);
            Fb2BodyParser bodyParser = new Fb2BodyParser(blockParser);
            DescriptionMapper descriptionMapper = new DescriptionMapper(blockParser);
            Fb2DescriptionReader descriptionReader = new Fb2DescriptionReader(staxFactory, descriptionMapper);

            while (xml.hasNext()) {
                int event = xml.next();
                if (event != XMLStreamConstants.START_ELEMENT) continue;

                String tag = xml.getLocalName();
                switch (tag) {
                    case "FictionBook" -> {
                        // Корневой элемент — просто проходим внутрь
                    }
                    case "description" -> {
                        if (description != null) {
                            throw InvalidFormatException.duplicateElement(
                                    fileName, "description", xml.getLocation());
                        }
                        description = descriptionReader.read(xml, fileName);
                    }
                    case "body" -> {
                        BodyDto body = bodyParser.parseBody(xml, fileName);
                        bodies.add(body);
                    }
                    case "binary" -> {
                        Resource resource = Fb2BinaryReader.readBinary(xml, fileName);
                        if (resource != null) {
                            resources.put(resource.id(), resource);
                        }
                    }
                    default -> {
                        log.debug("Skipping unknown top-level element: <{}>", tag);
                        skipElement(xml);
                    }
                }
            }

            xml.close();

            if (description == null) {
                throw InvalidFormatException.missingElement(fileName, "description");
            }
            if (bodies.isEmpty()) {
                throw InvalidFormatException.missingElement(fileName, "body");
            }

            // resources собран в LinkedHashMap (порядок документа); порядок сохраняет
            // конструктор FictionBookDto.
            return new FictionBookDto(description, List.copyOf(bodies), resources);

        } catch (XMLStreamException e) {
            throw new FictionBookException("XML parsing error in " + fileName, e);
        }
    }

    // ========================================================================
    // УТИЛИТЫ
    // ========================================================================

    /**
     * Пропускает текущий элемент целиком (включая всех потомков).
     */
    private void skipElement(XMLStreamReader xml) throws XMLStreamException {
        int depth = 1;
        while (xml.hasNext() && depth > 0) {
            int event = xml.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }
}
