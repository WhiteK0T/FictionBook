package org.tehlab.whitek0t.fictionbook.internal.reader.fb2;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.ResourceDataProvider;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.encoding.EncodingAwareInputStream;
import org.tehlab.whitek0t.fictionbook.encoding.EncodingDetector;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.exception.InvalidFormatException;
import org.tehlab.whitek0t.fictionbook.internal.parser.jackson.DescriptionMapper;
import org.tehlab.whitek0t.fictionbook.internal.parser.jackson.Fb2AnnotationJax;
import org.tehlab.whitek0t.fictionbook.internal.parser.jackson.Fb2DescriptionJax;
import org.tehlab.whitek0t.fictionbook.internal.parser.jackson.Fb2DocumentInfoJax;
import org.tehlab.whitek0t.fictionbook.internal.parser.jackson.Fb2HistoryJax;
import org.tehlab.whitek0t.fictionbook.internal.parser.jackson.Fb2TitleInfoJax;
import org.tehlab.whitek0t.fictionbook.internal.parser.jackson.MixedContentCapture;
import org.tehlab.whitek0t.fictionbook.internal.parser.stax.Fb2BlockParser;
import org.tehlab.whitek0t.fictionbook.internal.parser.stax.Fb2BodyParser;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.StringReader;
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

    private final XmlMapper jacksonMapper;
    private final XMLInputFactory staxFactory;

    public Fb2Reader() {
        this.jacksonMapper = XmlMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .build();

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
                        description = readDescription(xml, descriptionMapper, fileName);
                    }
                    case "body" -> {
                        BodyDto body = bodyParser.parseBody(xml, fileName);
                        bodies.add(body);
                    }
                    case "binary" -> {
                        Resource resource = readBinary(xml, fileName);
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

            return new FictionBookDto(description, List.copyOf(bodies), Map.copyOf(resources));

        } catch (XMLStreamException e) {
            throw new FictionBookException("XML parsing error in " + fileName, e);
        }
    }

    // ========================================================================
    // ПАРСИНГ <description> ЧЕРЕЗ JACKSON
    // ========================================================================

    private Description readDescription(XMLStreamReader xml,
                                        DescriptionMapper descriptionMapper,
                                        String fileName) throws FictionBookException {
        try {
            // Сериализуем всё поддерево <description> в строку (namespace-unaware),
            // чтобы (1) распарсить структурные поля Jackson'ом как и раньше и
            // (2) отдельно вытащить mixed content annotation/history, который
            // @JacksonXmlText захватить не способен (теряет вложенные <p>).
            String descXml = MixedContentCapture.serializeElement(xml);

            XMLStreamReader descReader = staxFactory.createXMLStreamReader(new StringReader(descXml));
            Fb2DescriptionJax jax;
            try {
                jax = jacksonMapper.readValue(descReader, Fb2DescriptionJax.class);
            } finally {
                descReader.close();
            }

            injectMixedContent(jax, descXml);

            return descriptionMapper.toDto(jax);
        } catch (Exception e) {
            throw new FictionBookException(
                    "Failed to parse <description> in " + fileName, e);
        }
    }

    /**
     * Достаёт сырой внутренний XML {@code <annotation>}/{@code <history>} из
     * поддерева description и кладёт его в {@code rawXml} соответствующих Jax-объектов,
     * чтобы дальше сработал штатный путь {@code DescriptionMapper → parseXmlFragment}.
     */
    private void injectMixedContent(Fb2DescriptionJax jax, String descXml) throws XMLStreamException {
        if (jax == null) {
            return;
        }

        String annotationXml = MixedContentCapture.extractInnerXml(descXml, staxFactory, "annotation");
        if (annotationXml != null) {
            if (jax.titleInfo == null) {
                jax.titleInfo = new Fb2TitleInfoJax();
            }
            if (jax.titleInfo.annotation == null) {
                jax.titleInfo.annotation = new Fb2AnnotationJax();
            }
            jax.titleInfo.annotation.rawXml = annotationXml;
        }

        String historyXml = MixedContentCapture.extractInnerXml(descXml, staxFactory, "history");
        if (historyXml != null) {
            if (jax.documentInfo == null) {
                jax.documentInfo = new Fb2DocumentInfoJax();
            }
            if (jax.documentInfo.history == null) {
                jax.documentInfo.history = new Fb2HistoryJax();
            }
            jax.documentInfo.history.rawXml = historyXml;
        }
    }

    // ========================================================================
    // ПАРСИНГ <binary> (EAGER-ЗАГРУЗКА)
    // ========================================================================

    /**
     * Читает {@code <binary>} элемент сразу в память.
     *
     * <p><b>Почему eager, а не lazy?</b></p>
     * <p>Ленивое чтение с {@code RandomAccessFile.seek()} требует:</p>
     * <ul>
     *   <li>Точных byte offset'ов в <b>исходном файле</b> (до перекодировки)</li>
     *   <li>Синхронизации с {@code BufferedInputStream} (который читает блоками)</li>
     *   <li>Учёта BOM и различий в длине символов между кодировками</li>
     * </ul>
     * <p>Это слишком сложно и хрупко. Проще загрузить бинарник сразу (обычно это
     * картинки 50-500 KB, что приемлемо по памяти).</p>
     */
    private Resource readBinary(XMLStreamReader xml, String fileName)
            throws XMLStreamException, FictionBookException {

        String id = xml.getAttributeValue(null, "id");
        String contentType = xml.getAttributeValue(null, "content-type");

        if (id == null || id.isBlank()) {
            log.warn("Skipping <binary> without id at line {}",
                    xml.getLocation().getLineNumber());
            skipElement(xml);
            return null;
        }

        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        // Читаем base64-текст из элемента
        StringBuilder base64Text = new StringBuilder();

        while (xml.hasNext()) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    base64Text.append(xml.getText());
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if ("binary".equals(xml.getLocalName())) {
                        // Декодируем base64 в байты
                        byte[] bytes = decodeBase64(base64Text.toString());
                        final String finalContentType = contentType;

                        ResourceDataProvider provider = () ->
                                new java.io.ByteArrayInputStream(bytes);

                        return new Resource(id, finalContentType, provider);
                    }
                }
            }
        }

        throw InvalidFormatException.missingElement(fileName, "</binary>");
    }

    /**
     * Декодирует base64-строку в байты.
     * Поддерживает как стандартный base64, так и MIME-вариант (с переносами строк).
     */
    private byte[] decodeBase64(String base64) {
        // Убираем пробельные символы (переносы строк, табуляции)
        String clean = base64.replaceAll("\\s+", "");

        try {
            return Base64.getDecoder().decode(clean);
        } catch (IllegalArgumentException e) {
            // Пробуем MIME-декодер (более терпимый к ошибкам)
            try {
                return Base64.getMimeDecoder().decode(clean);
            } catch (IllegalArgumentException e2) {
                log.warn("Failed to decode base64, returning empty array", e2);
                return new byte[0];
            }
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
