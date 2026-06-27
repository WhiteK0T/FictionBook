package org.tehlab.whitek0t.fictionbook.internal.reader.fb3;

import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.exception.InvalidFormatException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * FB3-контейнер — ZIP-архив в формате OPC (Open Packaging Conventions, как у DOCX).
 *
 * <p>Доступ к частям регистронезависимый; имена нормализуются (без ведущего {@code /},
 * {@code \} → {@code /}, нижний регистр). MIME-типы берутся из {@code [Content_Types].xml}.</p>
 *
 * <p>Две реализации с разной стратегией памяти:</p>
 * <ul>
 *   <li>{@link #openEager} — читает весь архив в память одним проходом
 *       ({@link ZipInputStream}). Для {@link Fb3Reader} (полное чтение): DTO переживает
 *       контейнер, поэтому держать открытый файловый дескриптор нельзя.</li>
 *   <li>{@link #openLazy} — random-access по {@link ZipFile}: части читаются по требованию
 *       ({@link #openPart}), картинки не разжимаются, пока их не запросят. Для
 *       {@code Fb3Streamer}; контейнер держится открытым до {@link #close()}.</li>
 * </ul>
 */
abstract class Fb3Package implements Closeable {

    /** Расширение (нижний регистр, без точки) → MIME-тип из {@code <Default>}. */
    private final Map<String, String> defaultContentTypes;
    /** Имя части (нормализованное) → MIME-тип из {@code <Override>}. */
    private final Map<String, String> overrideContentTypes;

    protected Fb3Package(Map<String, String> defaultContentTypes,
                         Map<String, String> overrideContentTypes) {
        this.defaultContentTypes = defaultContentTypes;
        this.overrideContentTypes = overrideContentTypes;
    }

    // ========================================================================
    // КОНТРАКТ
    // ========================================================================

    /** Возвращает байты части целиком (для небольших XML-частей) или {@code null}, если её нет. */
    abstract byte[] get(String partName);

    /** Проверяет наличие части. */
    abstract boolean has(String partName);

    /** Нормализованные имена всех частей архива. */
    abstract Set<String> names();

    /**
     * Открывает поток с байтами части (ленивый — не буферизует часть целиком).
     * Вызывающий обязан закрыть поток.
     *
     * @param partName имя части
     * @return свежий поток данных части
     * @throws IOException если часть отсутствует или произошла ошибка ввода-вывода
     */
    abstract InputStream openPart(String partName) throws IOException;

    /** По умолчанию закрывать нечего (eager-реализация); {@link Lazy} закрывает {@link ZipFile}. */
    @Override
    public void close() throws IOException {
        // no-op
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
    // ФАБРИКИ
    // ========================================================================

    /**
     * Распаковывает FB3-архив из потока в память (eager).
     *
     * @param in       поток с ZIP-данными
     * @param fileName имя файла (для сообщений об ошибках)
     * @param factory  StAX-фабрика для разбора {@code [Content_Types].xml}
     * @return контейнер, держащий все части в памяти
     * @throws FictionBookException если архив повреждён или не читается
     */
    static Fb3Package openEager(InputStream in, String fileName, XMLInputFactory factory)
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
        return new Eager(parts, defaults, overrides);
    }

    /**
     * Открывает FB3-архив по пути в eager-режиме.
     *
     * @param file     путь к {@code .fb3}-файлу
     * @param fileName имя файла (для сообщений об ошибках)
     * @param factory  StAX-фабрика для разбора {@code [Content_Types].xml}
     * @return контейнер, держащий все части в памяти
     * @throws FictionBookException если файл недоступен или архив повреждён
     */
    static Fb3Package openEager(Path file, String fileName, XMLInputFactory factory)
            throws FictionBookException {
        try (InputStream in = Files.newInputStream(file)) {
            return openEager(in, fileName, factory);
        } catch (IOException e) {
            throw InvalidFormatException.brokenArchive(fileName, "cannot open file", e);
        }
    }

    /**
     * Открывает FB3-архив по пути в ленивом режиме (random-access по {@link ZipFile}).
     * Контейнер держит файл открытым до {@link #close()}; части читаются по требованию.
     *
     * @param file     путь к {@code .fb3}-файлу
     * @param fileName имя файла (для сообщений об ошибках)
     * @param factory  StAX-фабрика для разбора {@code [Content_Types].xml}
     * @return ленивый контейнер
     * @throws FictionBookException если файл недоступен или архив повреждён
     */
    static Fb3Package openLazy(Path file, String fileName, XMLInputFactory factory)
            throws FictionBookException {
        ZipFile zf = null;
        try {
            zf = new ZipFile(file.toFile());

            // Карта нормализованное имя → исходное имя записи (для ZipFile.getEntry).
            Map<String, String> entryNames = new LinkedHashMap<>();
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (!e.isDirectory()) {
                    entryNames.putIfAbsent(normalize(e.getName()), e.getName());
                }
            }

            Map<String, String> defaults = new HashMap<>();
            Map<String, String> overrides = new HashMap<>();
            String ctEntry = entryNames.get(normalize("[Content_Types].xml"));
            if (ctEntry != null) {
                try (InputStream in = zf.getInputStream(zf.getEntry(ctEntry))) {
                    parseContentTypes(in.readAllBytes(), factory, defaults, overrides);
                }
            }
            return new Lazy(zf, entryNames, defaults, overrides);
        } catch (ZipException e) {
            closeQuietly(zf);
            throw InvalidFormatException.brokenArchive(fileName, e.getMessage(), e);
        } catch (IOException e) {
            closeQuietly(zf);
            throw InvalidFormatException.brokenArchive(fileName, "I/O error while reading archive", e);
        }
    }

    // ========================================================================
    // РЕАЛИЗАЦИИ
    // ========================================================================

    /** Eager: все части — в памяти ({@code Map<имя, байты>}). */
    private static final class Eager extends Fb3Package {

        private final Map<String, byte[]> parts;

        Eager(Map<String, byte[]> parts, Map<String, String> defaults, Map<String, String> overrides) {
            super(defaults, overrides);
            this.parts = parts;
        }

        @Override
        byte[] get(String partName) {
            return partName == null ? null : parts.get(normalize(partName));
        }

        @Override
        boolean has(String partName) {
            return partName != null && parts.containsKey(normalize(partName));
        }

        @Override
        Set<String> names() {
            return parts.keySet();
        }

        @Override
        InputStream openPart(String partName) throws IOException {
            byte[] data = get(partName);
            if (data == null) {
                throw new IOException("No such part: " + partName);
            }
            return new ByteArrayInputStream(data);
        }
    }

    /** Lazy: имена частей в памяти, байты читаются по требованию из открытого {@link ZipFile}. */
    private static final class Lazy extends Fb3Package {

        private final ZipFile zipFile;
        private final Map<String, String> entryNames;

        Lazy(ZipFile zipFile, Map<String, String> entryNames,
             Map<String, String> defaults, Map<String, String> overrides) {
            super(defaults, overrides);
            this.zipFile = zipFile;
            this.entryNames = entryNames;
        }

        @Override
        byte[] get(String partName) {
            String name = partName == null ? null : entryNames.get(normalize(partName));
            if (name == null) {
                return null;
            }
            try (InputStream in = zipFile.getInputStream(zipFile.getEntry(name))) {
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read FB3 part: " + partName, e);
            }
        }

        @Override
        boolean has(String partName) {
            return partName != null && entryNames.containsKey(normalize(partName));
        }

        @Override
        Set<String> names() {
            return entryNames.keySet();
        }

        @Override
        InputStream openPart(String partName) throws IOException {
            String name = partName == null ? null : entryNames.get(normalize(partName));
            if (name == null) {
                throw new IOException("No such part: " + partName);
            }
            return zipFile.getInputStream(zipFile.getEntry(name));
        }

        @Override
        public void close() throws IOException {
            zipFile.close();
        }
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

    private static void closeQuietly(ZipFile zf) {
        if (zf != null) {
            try {
                zf.close();
            } catch (IOException ignored) {
                // already failing — подавляем
            }
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
