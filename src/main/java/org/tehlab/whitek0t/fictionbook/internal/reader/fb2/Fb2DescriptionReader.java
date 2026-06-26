package org.tehlab.whitek0t.fictionbook.internal.reader.fb2;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.internal.parser.jackson.DescriptionMapper;
import org.tehlab.whitek0t.fictionbook.internal.parser.jackson.Fb2AnnotationJax;
import org.tehlab.whitek0t.fictionbook.internal.parser.jackson.Fb2DescriptionJax;
import org.tehlab.whitek0t.fictionbook.internal.parser.jackson.Fb2DocumentInfoJax;
import org.tehlab.whitek0t.fictionbook.internal.parser.jackson.Fb2HistoryJax;
import org.tehlab.whitek0t.fictionbook.internal.parser.jackson.Fb2TitleInfoJax;
import org.tehlab.whitek0t.fictionbook.internal.parser.jackson.MixedContentCapture;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;

/**
 * Гибридный разбор элемента {@code <description>}: структурные поля читаются
 * Jackson-ом, а mixed content {@code <annotation>}/{@code <history>} вытаскивается
 * отдельно (его {@code @JacksonXmlText} теряет, схлопывая вложенные {@code <p>}).
 *
 * <p>Выделено из {@code Fb2Reader}, чтобы переиспользоваться потоковым
 * {@link Fb2Streamer} без дублирования логики.</p>
 */
public final class Fb2DescriptionReader {

    private final XmlMapper jacksonMapper;
    private final XMLInputFactory staxFactory;
    private final DescriptionMapper descriptionMapper;

    /**
     * @param staxFactory       фабрика StAX (namespace-unaware), общая с ридером
     * @param descriptionMapper маппер Jax → DTO (несёт block-parser для mixed content)
     */
    public Fb2DescriptionReader(XMLInputFactory staxFactory, DescriptionMapper descriptionMapper) {
        this.staxFactory = staxFactory;
        this.descriptionMapper = descriptionMapper;
        this.jacksonMapper = XmlMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .build();
    }

    /**
     * Читает {@code <description>}; ридер должен стоять на открывающем теге.
     *
     * @param xml      StAX-ридер на {@code <description>}
     * @param fileName имя файла для сообщений об ошибках
     * @return разобранные метаданные книги
     * @throws FictionBookException при ошибке разбора
     */
    public Description read(XMLStreamReader xml, String fileName) throws FictionBookException {
        try {
            // Сериализуем всё поддерево <description> в строку (namespace-unaware),
            // чтобы (1) распарсить структурные поля Jackson'ом и (2) отдельно
            // вытащить mixed content annotation/history.
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
     * Достаёт сырой внутренний XML {@code <annotation>}/{@code <history>} из поддерева
     * description и кладёт его в {@code rawXml} соответствующих Jax-объектов, чтобы
     * сработал штатный путь {@code DescriptionMapper → parseXmlFragment}.
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
}
