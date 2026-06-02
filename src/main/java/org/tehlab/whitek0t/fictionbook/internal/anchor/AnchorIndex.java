package org.tehlab.whitek0t.fictionbook.internal.anchor;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable индекс якорей для быстрого поиска по ID.
 *
 * <p>Создаётся через {@link AnchorIndexBuilder} после завершения парсинга.</p>
 *
 * <p>Пример использования:</p>
 * <pre>{@code
 * AnchorIndex index = builder.build();
 *
 * // Поиск якоря по ссылке "#ch1"
 * Optional<AnchorInfo> anchor = index.resolve("#ch1");
 *
 * if (anchor.isPresent()) {
 *     AnchorInfo info = anchor.get();
 *     if (info.hasByteOffset()) {
 *         // Streaming API: seek к позиции
 *         channel.position(info.byteOffset());
 *     } else if (info.hasDomNode()) {
 *         // DOM API: используем объект напрямую
 *         Section section = (Section) info.domNode();
 *     }
 * }
 * }</pre>
 *
 * <p><b>Потокобезопасность:</b> Полностью потокобезопасен (immutable).</p>
 */
public class AnchorIndex {

    private final Map<String, AnchorInfo> anchors;

    /**
     * Создаёт индекс из готовой мапы.
     * Используется {@link AnchorIndexBuilder#build()}.
     */
    AnchorIndex(Map<String, AnchorInfo> anchors) {
        this.anchors = anchors;
    }

    /**
     * Создаёт пустой индекс.
     */
    public static AnchorIndex empty() {
        return new AnchorIndex(Map.of());
    }

    /**
     * Ищет якорь по ID.
     *
     * @param id идентификатор якоря (без символа {@code #})
     * @return информация о якоре, или {@link Optional#empty()} если не найден
     */
    public Optional<AnchorInfo> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(anchors.get(id));
    }

    /**
     * Разрешает ссылку вида {@code #anchor} в информацию о якоре.
     *
     * @param href ссылка (например, "#ch1" или "notes.xml#n1")
     * @return информация о якоре, или {@link Optional#empty()} если не найдена
     */
    public Optional<AnchorInfo> resolve(String href) {
        if (href == null || href.isBlank()) {
            return Optional.empty();
        }

        String id = extractIdFromHref(href);
        return find(id);
    }

    /**
     * Проверяет, существует ли якорь с таким ID.
     */
    public boolean contains(String id) {
        // id может быть null (например, для внешней ссылки extractIdFromHref → null),
        // а индекс хранится в immutable-мапе, которая на null-ключ бросает NPE.
        return id != null && anchors.containsKey(id);
    }

    /**
     * Проверяет, существует ли якорь для данной ссылки.
     */
    public boolean canResolve(String href) {
        String id = extractIdFromHref(href);
        return contains(id);
    }

    /**
     * @return количество якорей в индексе
     */
    public int size() {
        return anchors.size();
    }

    /**
     * @return true, если индекс пуст
     */
    public boolean isEmpty() {
        return anchors.isEmpty();
    }

    /**
     * @return все зарегистрированные якоря (immutable)
     */
    public Collection<AnchorInfo> getAll() {
        return Collections.unmodifiableCollection(anchors.values());
    }

    /**
     * @return все ID якорей (immutable)
     */
    public Collection<String> getAllIds() {
        return Collections.unmodifiableCollection(anchors.keySet());
    }

    /**
     * Извлекает ID из ссылки.
     *
     * <p>Примеры:</p>
     * <ul>
     *   <li>{@code "#ch1"} → {@code "ch1"}</li>
     *   <li>{@code "notes.xml#n1"} → {@code "n1"}</li>
     *   <li>{@code "http://example.com"} → {@code null} (внешняя ссылка)</li>
     * </ul>
     */
    private String extractIdFromHref(String href) {
        if (href == null) return null;

        // Внутренняя ссылка: #anchor
        if (href.startsWith("#")) {
            return href.substring(1);
        }

        // Ссылка на другой файл: file.xml#anchor
        int hashIndex = href.indexOf('#');
        if (hashIndex >= 0) {
            return href.substring(hashIndex + 1);
        }

        // Внешняя ссылка или просто текст — не является якорем
        return null;
    }

    @Override
    public String toString() {
        return String.format("AnchorIndex[%d anchors]", anchors.size());
    }
}