package org.tehlab.whitek0t.fictionbook.internal.reader.fb3;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Разбор OPC-связей (relationships) FB3: файлы {@code _rels/*.rels}, которые
 * связывают части архива по типам (книга → описание → тело → картинки).
 *
 * <p>Связь — это тройка {@code (Id, Type, Target)}. {@code Target} задаётся
 * относительно каталога части-источника и разрешается через {@link #resolveTarget}.</p>
 */
final class OpcRelationships {

    /**
     * Одна OPC-связь.
     *
     * @param id     идентификатор связи (на него ссылаются по {@code r:id}/{@code l:href})
     * @param type   URI типа связи (например, {@code .../relationships/body})
     * @param target цель связи относительно каталога части-источника
     */
    record Relationship(String id, String type, String target) {
    }

    private final List<Relationship> relationships;
    /** Каталог части-источника (нормализованный, с завершающим {@code /} либо пустой). */
    private final String baseDir;

    private OpcRelationships(List<Relationship> relationships, String baseDir) {
        this.relationships = relationships;
        this.baseDir = baseDir;
    }

    /**
     * Разбирает {@code .rels}-часть.
     *
     * @param xml      байты {@code .rels}-файла ({@code null} → пустой набор связей)
     * @param sourcePart нормализованное имя части, к которой относятся связи
     *                   (например, {@code "fb3/description.xml"})
     * @param factory  StAX-фабрика
     * @return разобранные связи; пустой набор, если {@code xml == null} или файл битый
     */
    static OpcRelationships parse(byte[] xml, String sourcePart, XMLInputFactory factory) {
        List<Relationship> list = new ArrayList<>();
        if (xml != null) {
            try {
                XMLStreamReader r = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
                try {
                    while (r.hasNext()) {
                        if (r.next() == XMLStreamConstants.START_ELEMENT
                                && "Relationship".equals(r.getLocalName())) {
                            list.add(new Relationship(
                                    r.getAttributeValue(null, "Id"),
                                    r.getAttributeValue(null, "Type"),
                                    r.getAttributeValue(null, "Target")
                            ));
                        }
                    }
                } finally {
                    r.close();
                }
            } catch (XMLStreamException e) {
                // Битый .rels — не фатально, возвращаем то, что собрали.
            }
        }
        return new OpcRelationships(list, baseDirOf(sourcePart));
    }

    /** Все связи. */
    List<Relationship> all() {
        return relationships;
    }

    /**
     * Возвращает первую связь, чей {@code Type} оканчивается на {@code typeSuffix}
     * (регистронезависимо), или {@code null}.
     */
    Relationship findByTypeSuffix(String typeSuffix) {
        String suffix = typeSuffix.toLowerCase();
        for (Relationship rel : relationships) {
            if (rel.type() != null && rel.type().toLowerCase().endsWith(suffix)) {
                return rel;
            }
        }
        return null;
    }

    /**
     * Разрешает {@code Target} связи в нормализованное имя части архива относительно
     * каталога части-источника. Абсолютные цели (начинающиеся с {@code /}) берутся
     * от корня пакета.
     *
     * @param target цель связи ({@code null} → {@code null})
     * @return нормализованное имя части или {@code null}
     */
    String resolveTarget(String target) {
        if (target == null) return null;
        String t = target.replace('\\', '/');
        if (t.startsWith("/")) {
            return Fb3Package.normalize(t);
        }
        return Fb3Package.normalize(baseDir + t);
    }

    /**
     * Имя {@code .rels}-части для данной части: {@code dir/_rels/name.ext.rels}
     * (для корня пакета — {@code _rels/.rels}).
     *
     * @param part нормализованное имя части ({@code null}/пусто → корневой {@code _rels/.rels})
     * @return нормализованное имя соответствующей {@code .rels}-части
     */
    static String relsPathFor(String part) {
        if (part == null || part.isEmpty()) {
            return "_rels/.rels";
        }
        String p = Fb3Package.normalize(part);
        int slash = p.lastIndexOf('/');
        String dir = slash >= 0 ? p.substring(0, slash + 1) : "";
        String name = slash >= 0 ? p.substring(slash + 1) : p;
        return dir + "_rels/" + name + ".rels";
    }

    /** Каталог части (с завершающим {@code /}), либо пустая строка для корня. */
    private static String baseDirOf(String sourcePart) {
        if (sourcePart == null) return "";
        String p = Fb3Package.normalize(sourcePart);
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(0, slash + 1) : "";
    }
}
