package org.tehlab.whitek0t.fictionbook.internal.reader.fb3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.ResourceDataProvider;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.dto.inline.ImageRef;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.exception.InvalidFormatException;
import org.tehlab.whitek0t.fictionbook.internal.parser.stax.Fb2BlockParser;
import org.tehlab.whitek0t.fictionbook.internal.sanitizer.FictionBookDtoTransformer;

import javax.xml.stream.XMLInputFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Читает FB3-файл (OPC/ZIP-контейнер) в {@link FictionBookDto}.
 *
 * <p>Поток разбора: распаковка архива ({@link Fb3Package}) → навигация по OPC-связям
 * {@code _rels/*.rels} (книга → {@code description.xml} → {@code body.xml} + картинки)
 * → разбор {@code description.xml} ({@link Fb3DescriptionParser}) и {@code body.xml}
 * ({@link Fb3BodyParser}) → регистрация картинок как {@link Resource} и переписывание
 * ссылок {@code <img>} на якоря вида {@code "#id"}, чтобы рендеринг работал единообразно
 * с FB2.</p>
 *
 * <p>Как и FB2-ридер, режим прощающий: при отсутствии {@code .rels} используются
 * стандартные пути ({@code fb3/description.xml}, {@code fb3/body.xml}); неизвестные
 * элементы пропускаются.</p>
 */
public class Fb3Reader {

    private static final Logger log = LoggerFactory.getLogger(Fb3Reader.class);

    private static final String DEFAULT_DESCRIPTION_PART = "fb3/description.xml";
    private static final String DEFAULT_BODY_PART = "fb3/body.xml";

    private final XMLInputFactory factory;
    private final Fb2BlockParser blockParser;
    private final Fb3DescriptionParser descriptionParser;
    private final Fb3BodyParser bodyParser;

    public Fb3Reader() {
        this.factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, false);

        this.blockParser = new Fb2BlockParser(factory);
        this.descriptionParser = new Fb3DescriptionParser(factory, blockParser);
        this.bodyParser = new Fb3BodyParser(factory, blockParser);
    }

    /**
     * Читает FB3-файл по указанному пути.
     *
     * @param file путь к {@code .fb3}-файлу
     * @return разобранная книга
     * @throws FictionBookException при ошибке распаковки или разбора
     */
    public FictionBookDto read(Path file) throws FictionBookException {
        String fileName = InvalidFormatException.extractFileName(file);
        return build(Fb3Package.open(file, fileName, factory), fileName);
    }

    /**
     * Читает FB3 из потока (для тестов / нестандартных источников).
     *
     * @param in поток с данными {@code .fb3}-архива
     * @return разобранная книга
     * @throws FictionBookException при ошибке распаковки или разбора
     */
    public FictionBookDto read(InputStream in) throws FictionBookException {
        return build(Fb3Package.open(in, "<stream>", factory), "<stream>");
    }

    // ========================================================================
    // ОСНОВНОЙ КОНВЕЙЕР
    // ========================================================================

    private FictionBookDto build(Fb3Package pkg, String fileName) throws FictionBookException {
        // 1. Корневые связи: книга → description.xml
        OpcRelationships rootRels = OpcRelationships.parse(
                pkg.get(OpcRelationships.relsPathFor(null)), null, factory);
        String descPart = resolveDescriptionPart(pkg, rootRels);

        byte[] descBytes = pkg.get(descPart);
        if (descBytes == null) {
            throw InvalidFormatException.missingFb3Entry(fileName, descPart);
        }

        // 2. Связи описания: тело, сноски, обложка
        OpcRelationships descRels = OpcRelationships.parse(
                pkg.get(OpcRelationships.relsPathFor(descPart)), descPart, factory);

        String bodyPart = resolveBodyPart(pkg, descRels);
        byte[] bodyBytes = pkg.get(bodyPart);
        if (bodyBytes == null) {
            throw InvalidFormatException.missingFb3Entry(fileName, bodyPart);
        }

        List<String> coverIds = resolveCoverIds(descRels);

        // 3. Разбор описания и тела
        Description description = descriptionParser.parse(descBytes, fileName, coverIds);

        List<BodyDto> bodies = new ArrayList<>(bodyParser.parse(bodyBytes, null, fileName));
        addNotesBody(pkg, descRels, bodies, fileName);

        if (bodies.isEmpty()) {
            throw InvalidFormatException.missingElement(fileName, "fb3-body");
        }

        // 4. Картинки → ресурсы + карта переписывания ссылок <img>
        Map<String, Resource> resources = new LinkedHashMap<>();
        Map<String, String> hrefToId = new HashMap<>();
        collectImages(pkg, bodyPart, descRels, resources, hrefToId);

        FictionBookDto dto = new FictionBookDto(description, bodies, resources);
        return rewriteImageHrefs(dto, hrefToId);
    }

    // ========================================================================
    // НАВИГАЦИЯ ПО OPC-СВЯЗЯМ
    // ========================================================================

    private String resolveDescriptionPart(Fb3Package pkg, OpcRelationships rootRels) {
        var rel = rootRels.findByTypeSuffix("/book");
        if (rel != null) {
            String part = rootRels.resolveTarget(rel.target());
            if (part != null && pkg.has(part)) return part;
        }
        for (OpcRelationships.Relationship r : rootRels.all()) {
            String part = rootRels.resolveTarget(r.target());
            if (part != null && part.endsWith("description.xml") && pkg.has(part)) {
                return part;
            }
        }
        return DEFAULT_DESCRIPTION_PART;
    }

    private String resolveBodyPart(Fb3Package pkg, OpcRelationships descRels) {
        var rel = descRels.findByTypeSuffix("/body");
        if (rel != null) {
            String part = descRels.resolveTarget(rel.target());
            if (part != null && pkg.has(part)) return part;
        }
        return DEFAULT_BODY_PART;
    }

    private List<String> resolveCoverIds(OpcRelationships descRels) {
        List<String> ids = new ArrayList<>();
        for (OpcRelationships.Relationship r : descRels.all()) {
            String type = r.type() == null ? "" : r.type().toLowerCase();
            if (type.endsWith("/cover") || type.contains("thumbnail")) {
                String part = descRels.resolveTarget(r.target());
                if (part != null) {
                    ids.add(lastSegment(part));
                }
            }
        }
        return ids;
    }

    private void addNotesBody(Fb3Package pkg, OpcRelationships descRels,
                              List<BodyDto> bodies, String fileName) throws FictionBookException {
        var rel = descRels.findByTypeSuffix("/notes");
        if (rel == null) return;
        String notesPart = descRels.resolveTarget(rel.target());
        byte[] notesBytes = pkg.get(notesPart);
        if (notesBytes != null) {
            bodies.addAll(bodyParser.parse(notesBytes, "notes", fileName));
        }
    }

    // ========================================================================
    // КАРТИНКИ
    // ========================================================================

    private void collectImages(Fb3Package pkg, String bodyPart, OpcRelationships descRels,
                               Map<String, Resource> resources, Map<String, String> hrefToId) {
        String bodyDir = directoryOf(bodyPart);

        // 1. Регистрируем все части-картинки как ресурсы (даже без ссылок — напр. обложку).
        for (String part : pkg.names()) {
            if (!isImagePart(pkg, part)) continue;

            String id = uniqueId(lastSegment(part), resources);
            byte[] data = pkg.get(part);
            String ct = imageContentType(pkg, part);
            ResourceDataProvider provider = () -> new ByteArrayInputStream(data);
            resources.put(id, new Resource(id, ct, provider));

            // Возможные формы ссылки на эту картинку в теле:
            putHref(hrefToId, part, id);                 // полный путь: fb3/img/x.jpg
            putHref(hrefToId, lastSegment(part), id);    // имя файла: x.jpg
            if (part.startsWith(bodyDir)) {
                putHref(hrefToId, part.substring(bodyDir.length()), id); // относительно тела: img/x.jpg
            }
        }

        // 2. Связи тела: <img l:href="rId"> → relationship Id → целевая часть.
        OpcRelationships bodyRels = OpcRelationships.parse(
                pkg.get(OpcRelationships.relsPathFor(bodyPart)), bodyPart, factory);
        mapImageRelationships(bodyRels, resources, hrefToId);
        // Картинки также могут быть привязаны к описанию (обложка).
        mapImageRelationships(descRels, resources, hrefToId);
    }

    private void mapImageRelationships(OpcRelationships rels, Map<String, Resource> resources,
                                       Map<String, String> hrefToId) {
        for (OpcRelationships.Relationship r : rels.all()) {
            if (r.id() == null) continue;
            String part = rels.resolveTarget(r.target());
            if (part == null) continue;
            String id = lastSegment(part);
            if (resources.containsKey(id)) {
                putHref(hrefToId, r.id(), id);      // ссылка по Id связи (rId7)
                putHref(hrefToId, r.target(), id);  // ссылка по исходной цели
            }
        }
    }

    private FictionBookDto rewriteImageHrefs(FictionBookDto dto, Map<String, String> hrefToId) {
        if (hrefToId.isEmpty()) return dto;
        return FictionBookDtoTransformer.transform(dto)
                .onInlineElement(inline -> {
                    if (inline instanceof ImageRef img && img.href() != null) {
                        String id = hrefToId.get(normalizeHref(img.href()));
                        if (id != null) {
                            return new ImageRef("#" + id, img.alt());
                        }
                    }
                    return inline;
                })
                .apply();
    }

    // ========================================================================
    // УТИЛИТЫ
    // ========================================================================

    private boolean isImagePart(Fb3Package pkg, String part) {
        String ct = pkg.contentType(part);
        if (ct != null && ct.toLowerCase().startsWith("image/")) return true;
        // Запасной вариант — по расположению/расширению, если [Content_Types] неполон.
        return (part.contains("/img/") || part.startsWith("img/"))
                && imageContentTypeByExtension(part) != null;
    }

    private String imageContentType(Fb3Package pkg, String part) {
        String ct = pkg.contentType(part);
        if (ct != null) return ct;
        String byExt = imageContentTypeByExtension(part);
        return byExt != null ? byExt : "application/octet-stream";
    }

    private String imageContentTypeByExtension(String part) {
        int dot = part.lastIndexOf('.');
        if (dot < 0) return null;
        return switch (part.substring(dot + 1).toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "bmp" -> "image/bmp";
            case "tif", "tiff" -> "image/tiff";
            default -> null;
        };
    }

    private void putHref(Map<String, String> map, String href, String id) {
        if (href == null) return;
        map.putIfAbsent(normalizeHref(href), id);
    }

    /** Нормализует ссылку: убирает ведущие {@code #} и {@code /}, нижний регистр. */
    private String normalizeHref(String href) {
        String h = href.trim().replace('\\', '/');
        while (h.startsWith("#")) {
            h = h.substring(1);
        }
        if (h.startsWith("/")) {
            h = h.substring(1);
        }
        return h.toLowerCase();
    }

    private String uniqueId(String base, Map<String, Resource> resources) {
        if (!resources.containsKey(base)) return base;
        int i = 2;
        while (resources.containsKey(base + "-" + i)) {
            i++;
        }
        return base + "-" + i;
    }

    private static String lastSegment(String part) {
        int slash = part.lastIndexOf('/');
        return slash >= 0 ? part.substring(slash + 1) : part;
    }

    private static String directoryOf(String part) {
        int slash = part.lastIndexOf('/');
        return slash >= 0 ? part.substring(0, slash + 1) : "";
    }
}
