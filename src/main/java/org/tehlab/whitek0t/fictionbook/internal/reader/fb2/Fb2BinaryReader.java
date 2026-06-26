package org.tehlab.whitek0t.fictionbook.internal.reader.fb2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.ResourceDataProvider;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.exception.InvalidFormatException;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * Читает FB2-элемент {@code <binary>} (base64-картинку) сразу в память (eager).
 *
 * <p><b>Почему eager, а не lazy?</b> Ленивое чтение с {@code RandomAccessFile.seek()}
 * требует точных byte offset'ов в исходном файле (до перекодировки), синхронизации с
 * буферизацией и учёта различий длины символов между кодировками — слишком хрупко.
 * Бинарники в FB2 обычно небольшие (50–500 KB), их проще загрузить целиком.</p>
 *
 * <p>Выделено из {@code Fb2Reader}, чтобы переиспользоваться потоковым
 * {@link Fb2Streamer}.</p>
 */
public final class Fb2BinaryReader {

    private static final Logger log = LoggerFactory.getLogger(Fb2BinaryReader.class);

    private Fb2BinaryReader() {
    }

    /**
     * Читает один {@code <binary>}; ридер должен стоять на открывающем теге.
     *
     * @param xml      StAX-ридер на {@code <binary>}
     * @param fileName имя файла для сообщений об ошибках
     * @return ресурс или {@code null}, если у элемента нет {@code id}
     * @throws FictionBookException если элемент не закрыт
     */
    public static Resource readBinary(XMLStreamReader xml, String fileName)
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

        StringBuilder base64Text = new StringBuilder();

        while (xml.hasNext()) {
            int event = xml.next();
            switch (event) {
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA ->
                        base64Text.append(xml.getText());
                case XMLStreamConstants.END_ELEMENT -> {
                    if ("binary".equals(xml.getLocalName())) {
                        byte[] bytes = decodeBase64(base64Text.toString());
                        final String finalContentType = contentType;
                        ResourceDataProvider provider = () -> new ByteArrayInputStream(bytes);
                        return new Resource(id, finalContentType, provider);
                    }
                }
            }
        }

        throw InvalidFormatException.missingElement(fileName, "</binary>");
    }

    /**
     * Декодирует base64-строку в байты. Поддерживает как стандартный base64, так и
     * MIME-вариант (с переносами строк).
     */
    private static byte[] decodeBase64(String base64) {
        String clean = base64.replaceAll("\\s+", "");
        try {
            return Base64.getDecoder().decode(clean);
        } catch (IllegalArgumentException e) {
            try {
                return Base64.getMimeDecoder().decode(clean);
            } catch (IllegalArgumentException e2) {
                log.warn("Failed to decode base64, returning empty array", e2);
                return new byte[0];
            }
        }
    }

    private static void skipElement(XMLStreamReader xml) throws XMLStreamException {
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
