package org.tehlab.whitek0t.fictionbook.internal.reader.fb3;

import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.ResourceDataProvider;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.inline.ImageRef;
import org.tehlab.whitek0t.fictionbook.internal.sanitizer.FictionBookDtoTransformer;

import javax.xml.stream.XMLInputFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Раскладка FB3-контейнера: единый источник правды по OPC-навигации и картинкам,
 * общий для {@link Fb3Reader} (полное чтение) и {@code Fb3Streamer} (потоковое).
 *
 * <p>Резолвит части контейнера по связям {@code _rels/*.rels} (книга →
 * {@code description.xml} → {@code body.xml} + сноски + обложка) и — лениво —
 * собирает картинки в {@link Resource}-ы вместе с картой переписывания ссылок
 * {@code <img>} на якоря вида {@code "#id"}.</p>
 *
 * <p>Режим прощающий: при отсутствии {@code .rels} используются стандартные пути
 * ({@code fb3/description.xml}, {@code fb3/body.xml}).</p>
 */
final class Fb3Layout {

    private static final String DEFAULT_DESCRIPTION_PART = "fb3/description.xml";
    private static final String DEFAULT_BODY_PART = "fb3/body.xml";

    private final Fb3Package pkg;
    private final XMLInputFactory factory;

    /** Часть {@code description.xml}. */
    final String descPart;
    /** Связи описания (тело, сноски, обложка). */
    final OpcRelationships descRels;
    /** Часть основного тела {@code body.xml}. */
    final String bodyPart;
    /** Часть тела сносок или {@code null}, если сносок нет. */
    final String notesPart;
    /** Имена частей-обложек (для {@code coverImageIds} описания). */
    final List<String> coverIds;

    /** Картинки → ресурсы; строится лениво при первом обращении. */
    private Map<String, Resource> resources;
    /** Нормализованная ссылка {@code <img>} → id ресурса; строится вместе с {@link #resources}. */
    private Map<String, String> hrefToId;

    private Fb3Layout(Fb3Package pkg, XMLInputFactory factory, String descPart,
                      OpcRelationships descRels, String bodyPart, String notesPart,
                      List<String> coverIds) {
        this.pkg = pkg;
        this.factory = factory;
        this.descPart = descPart;
        this.descRels = descRels;
        this.bodyPart = bodyPart;
        this.notesPart = notesPart;
        this.coverIds = coverIds;
    }

    /**
     * Разбирает OPC-связи контейнера и резолвит части описания/тела/сносок/обложки.
     *
     * @param pkg     распакованный контейнер
     * @param factory StAX-фабрика для разбора {@code .rels}
     * @return раскладка контейнера
     */
    static Fb3Layout resolve(Fb3Package pkg, XMLInputFactory factory) {
        OpcRelationships rootRels = OpcRelationships.parse(
                pkg.get(OpcRelationships.relsPathFor(null)), null, factory);
        String descPart = resolveDescriptionPart(pkg, rootRels);

        OpcRelationships descRels = OpcRelationships.parse(
                pkg.get(OpcRelationships.relsPathFor(descPart)), descPart, factory);

        String bodyPart = resolveBodyPart(pkg, descRels);
        String notesPart = resolveNotesPart(descRels);
        List<String> coverIds = resolveCoverIds(descRels);

        return new Fb3Layout(pkg, factory, descPart, descRels, bodyPart, notesPart, coverIds);
    }

    /** Картинки контейнера как ресурсы (ленивая инициализация). */
    Map<String, Resource> resources() {
        ensureImages();
        return resources;
    }

    /** Карта переписывания ссылок {@code <img>} → {@code id} ресурса (ленивая инициализация). */
    Map<String, String> hrefToId() {
        ensureImages();
        return hrefToId;
    }

    private void ensureImages() {
        if (resources != null) {
            return;
        }
        Map<String, Resource> res = new LinkedHashMap<>();
        Map<String, String> href = new HashMap<>();
        collectImages(res, href);
        resources = res;
        hrefToId = href;
    }

    // ========================================================================
    // НАВИГАЦИЯ ПО OPC-СВЯЗЯМ
    // ========================================================================

    private static String resolveDescriptionPart(Fb3Package pkg, OpcRelationships rootRels) {
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

    private static String resolveBodyPart(Fb3Package pkg, OpcRelationships descRels) {
        var rel = descRels.findByTypeSuffix("/body");
        if (rel != null) {
            String part = descRels.resolveTarget(rel.target());
            if (part != null && pkg.has(part)) return part;
        }
        return DEFAULT_BODY_PART;
    }

    private static String resolveNotesPart(OpcRelationships descRels) {
        var rel = descRels.findByTypeSuffix("/notes");
        return rel == null ? null : descRels.resolveTarget(rel.target());
    }

    private static List<String> resolveCoverIds(OpcRelationships descRels) {
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

    // ========================================================================
    // КАРТИНКИ
    // ========================================================================

    private void collectImages(Map<String, Resource> resources, Map<String, String> hrefToId) {
        String bodyDir = directoryOf(bodyPart);

        // 1. Регистрируем все части-картинки как ресурсы (даже без ссылок — напр. обложку).
        for (String part : pkg.names()) {
            if (!isImagePart(part)) continue;

            String id = uniqueId(lastSegment(part), resources);
            byte[] data = pkg.get(part);
            String ct = imageContentType(part);
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

    /**
     * Переписывает ссылки картинок в книге на якоря {@code "#id"}.
     *
     * @param dto      разобранная книга
     * @param hrefToId карта переписывания ({@link #hrefToId()})
     * @return книга с переписанными ссылками (та же, если карта пуста)
     */
    static FictionBookDto rewriteImageHrefs(FictionBookDto dto, Map<String, String> hrefToId) {
        if (hrefToId == null || hrefToId.isEmpty()) return dto;
        return FictionBookDtoTransformer.transform(dto)
                .onInlineElement(inline -> rewriteInline(inline, hrefToId))
                .apply();
    }

    /**
     * Переписывает ссылки картинок в одной секции (для потокового чтения).
     *
     * @param section  секция
     * @param hrefToId карта переписывания ({@link #hrefToId()})
     * @return секция с переписанными ссылками (та же, если карта пуста)
     */
    static Section rewriteImageHrefs(Section section, Map<String, String> hrefToId) {
        if (section == null || hrefToId == null || hrefToId.isEmpty()) return section;
        FictionBookDto wrapper = new FictionBookDto(
                null, List.of(new BodyDto(null, List.of(section))), Map.of());
        FictionBookDto rewritten = rewriteImageHrefs(wrapper, hrefToId);
        return rewritten.bodies().getFirst().sections().getFirst();
    }

    private static org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement rewriteInline(
            org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement inline, Map<String, String> hrefToId) {
        if (inline instanceof ImageRef(String href, String alt) && href != null) {
            String id = hrefToId.get(normalizeHref(href));
            if (id != null) {
                return new ImageRef("#" + id, alt);
            }
        }
        return inline;
    }

    // ========================================================================
    // УТИЛИТЫ
    // ========================================================================

    private boolean isImagePart(String part) {
        String ct = pkg.contentType(part);
        if (ct != null && ct.toLowerCase().startsWith("image/")) return true;
        // Запасной вариант — по расположению/расширению, если [Content_Types] неполон.
        return (part.contains("/img/") || part.startsWith("img/"))
                && imageContentTypeByExtension(part) != null;
    }

    private String imageContentType(String part) {
        String ct = pkg.contentType(part);
        if (ct != null) return ct;
        String byExt = imageContentTypeByExtension(part);
        return byExt != null ? byExt : "application/octet-stream";
    }

    private static String imageContentTypeByExtension(String part) {
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

    private static void putHref(Map<String, String> map, String href, String id) {
        if (href == null) return;
        map.putIfAbsent(normalizeHref(href), id);
    }

    /** Нормализует ссылку: убирает ведущие {@code #} и {@code /}, нижний регистр. */
    private static String normalizeHref(String href) {
        String h = href.trim().replace('\\', '/');
        while (h.startsWith("#")) {
            h = h.substring(1);
        }
        if (h.startsWith("/")) {
            h = h.substring(1);
        }
        return h.toLowerCase();
    }

    private static String uniqueId(String base, Map<String, Resource> resources) {
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
