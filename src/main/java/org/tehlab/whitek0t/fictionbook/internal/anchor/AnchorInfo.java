package org.tehlab.whitek0t.fictionbook.internal.anchor;

/**
 * Информация о якоре (элементе с атрибутом {@code id}).
 *
 * <p>Используется для быстрого перехода по внутренним ссылкам вида {@code #anchor}.</p>
 *
 * @param id          идентификатор якоря (значение атрибута {@code id})
 * @param elementType тип элемента (например, "section", "binary", "note")
 * @param byteOffset  позиция в файле в байтах (для seek в Streaming API), или -1 если неизвестно
 * @param lineNumber  номер строки в XML (для отладки), или -1 если неизвестно
 * @param bodyName    имя тела книги, в котором находится якорь (null для основного, "notes" для примечаний)
 * @param domNode     ссылка на объект в памяти (для DOM API), или null для Streaming API
 */
public record AnchorInfo(
        String id,
        String elementType,
        long byteOffset,
        int lineNumber,
        String bodyName,
        Object domNode
) {
    public AnchorInfo {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Anchor id must not be null or blank");
        }
    }

    /**
     * @return true, если известна позиция в файле (для seek)
     */
    public boolean hasByteOffset() {
        return byteOffset >= 0;
    }

    /**
     * @return true, если есть ссылка на DOM-объект
     */
    public boolean hasDomNode() {
        return domNode != null;
    }

    /**
     * @return true, если якорь находится в примечаниях (body name="notes")
     */
    public boolean isNote() {
        return "notes".equals(bodyName);
    }

    @Override
    public String toString() {
        return String.format(
                "AnchorInfo[id='%s', type='%s', offset=%d, line=%d, body='%s']",
                id, elementType, byteOffset, lineNumber, bodyName
        );
    }
}