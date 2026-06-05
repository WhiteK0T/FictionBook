package org.tehlab.whitek0t.fictionbook.internal.reader.fb3;

import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.exception.InvalidFormatException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * FB3-контейнер — ZIP-архив в формате OPC (Open Packaging Conventions, как у DOCX).
 *
 * <p>Читает весь архив в память одним проходом: имена частей (parts) и их байты,
 * а также карту MIME-типов из {@code [Content_Types].xml}. Доступ к частям —
 * регистронезависимый, имена нормализуются (без ведущего {@code /}, {@code \} → {@code /},
 * нижний регистр).</p>
 *
 * <p>Бинарники грузятся целиком (eager), как и в FB2-ридере: FB3-картинки обычно
 * 50–500 KB, что приемлемо по памяти, а seek по ZIP с дефлейтом всё равно невозможен.</p>
 */
final class Fb3Package {

    /** Имя части → её байты. Ключ нормализован (см. {@link #normalize}). */
    private final Map<String, byte[]> parts;
    /** Расширение (нижний регистр, без точки) → MIME-тип из {@code <Default>}. */
    private final Map<String, String> defaultContentTypes;
    /** Имя части (нормализованное) → MIME-тип из {@code <Override>}. */
    private final Map<String, String> overrideContentTypes;

    private Fb3Package(Map<String, byte[]> parts,
                       Map<String, String> defaultContentTypes,
                       Map<String, String> overrideContentTypes) {
        this.parts = parts;
        this.defaultContentTypes = defaultContentTypes;
        this.overrideContentTypes = overrideContentTypes;
    }

    /**
     * Распаковывает FB3-архив из потока в память.
     *
     * @param in       поток с ZIP-данными
     * @param fileName имя файла (для сообщений об ошибках)
     * @param factory  StAX-фабрика для разбора {@code [Content_Types].xml}
     * @return готовый контейнер
     * @throws FictionBookException если архив повреждён или не читается
     */
    static Fb3Package open(InputStream in, String fileName, XMLInputFactory factory)
            throws FictionBookException {

        Map<String, byte[]> parts = new LinkedHashMap<>();

        try (ZipInputStream zip = new ZipInputStream(in)) {
            var entry = zip.getNextEntry();
            if (entry == null) {
                throw InvalidFormatException.brokenArchive(fileName, "archive is empty or not a ZIP", null);
            }
            while (entry != null) {
                if (!entry.isDirectory()) {
                    parts.put(normalize(entry.getName()), zip.readAllBytes());
                }
                zip.closeEntry();
                entry = zip.getNextEntry();
            }
        } catch (ZipException e) {
            throw InvalidFormatException.brokenArchive(fileName, e.getMessage(), e);
        } catch (IOException e) {
            throw InvalidFormatException.brokenArchive(fileName, "I/O error while reading archive", e);
        }

        Map<String, String> defaults = new HashMap<>();
        Map<String, String> overrides = new HashMap<>();
        byte[] contentTypes = parts.get(normalize("[Content_Types].xml"));
        if (contentTypes != null) {
            parseContentTypes(contentTypes, factory, defaults, overrides);
        }

        return new Fb3Package(parts, defaults, overrides);
    }

    /**
     * Открывает FB3-архив по пути.
     *
     * @param file     путь к {@code .fb3}-файлу
     * @param fileName имя файла (для сообщений об ошибках)
     * @param factory  StAX-фабрика для разбора {@code [Content_Types].xml}
     * @return готовый контейнер
     * @throws FictionBookException если файл недоступен или архив повреждён
     */
    static Fb3Package open(Path file, String fileName, XMLInputFactory factory)
            throws FictionBookException {
        try (InputStream in = Files.newInputStream(file)) {
            return open(in, fileName, factory);
        } catch (IOException e) {
            throw InvalidFormatException.brokenArchive(fileName, "cannot open file", e);
        }
    }

    /**
     * Возвращает байты части по имени (регистронезависимо) или {@code null}, если её нет.
     */
    byte[] get(String partName) {
        if (partName == null) return null;
        return parts.get(normalize(partName));
    }

    /** Проверяет наличие части. */
    boolean has(String partName) {
        return partName != null && parts.containsKey(normalize(partName));
    }

    /** Возвращает нормализованные имена всех частей архива. */
    Set<String> names() {
        return parts.keySet();
    }

    /**
     * Определяет MIME-тип части: сначала по {@code <Override>}, затем по расширению
     * ({@code <Default>}); {@code null}, если тип неизвестен.
     */
    String contentType(String partName) {
        if (partName == null) return null;
        String key = normalize(partName);
        String override = overrideContentTypes.get(key);
        if (override != null) return override;

        int dot = key.lastIndexOf('.');
        if (dot >= 0 && dot < key.length() - 1) {
            return defaultContentTypes.get(key.substring(dot + 1));
        }
        return null;
    }

    // ========================================================================
    // ВНУТРЕННЕЕ
    // ========================================================================

    private static void parseContentTypes(byte[] xml, XMLInputFactory factory,
                                          Map<String, String> defaults,
                                          Map<String, String> overrides) throws FictionBookException {
        try {
            XMLStreamReader r = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
            try {
                while (r.hasNext()) {
                    if (r.next() != XMLStreamConstants.START_ELEMENT) continue;
                    switch (r.getLocalName()) {
                        case "Default" -> {
                            String ext = r.getAttributeValue(null, "Extension");
                            String ct = r.getAttributeValue(null, "ContentType");
                            if (ext != null && ct != null) {
                                defaults.put(ext.toLowerCase(), ct.trim());
                            }
                        }
                        case "Override" -> {
                            String part = r.getAttributeValue(null, "PartName");
                            String ct = r.getAttributeValue(null, "ContentType");
                            if (part != null && ct != null) {
                                overrides.put(normalize(part), ct.trim());
                            }
                        }
                        default -> { /* <Types> и прочее игнорируем */ }
                    }
                }
            } finally {
                r.close();
            }
        } catch (XMLStreamException e) {
            // [Content_Types].xml битый — не фатально: MIME-типы определим по расширению.
            // Молча продолжаем с тем, что успели собрать.
        }
    }

    /**
     * Нормализует имя части: {@code \} → {@code /}, убирает ведущий {@code /},
     * приводит к нижнему регистру (OPC-имена регистронезависимы по сравнению).
     */
    static String normalize(String name) {
        String n = name.replace('\\', '/');
        if (n.startsWith("/")) {
            n = n.substring(1);
        }
        return n.toLowerCase();
    }
}
