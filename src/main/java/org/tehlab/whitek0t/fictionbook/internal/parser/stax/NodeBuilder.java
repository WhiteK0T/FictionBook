package org.tehlab.whitek0t.fictionbook.internal.parser.stax;

/**
 * Базовый интерфейс для всех строителей узлов FB2-дерева.
 *
 * <p>Используется в стековом парсере для обработки mixed content
 * (текст + вложенные теги в любом порядке).</p>
 *
 * <p>Жизненный цикл:</p>
 * <ol>
 *   <li>Создаётся при встрече открывающего тега</li>
 *   <li>Накапливает текст через {@link #appendText(String)}</li>
 *   <li>Принимает дочерние узлы через {@link #addChild(Object)}</li>
 *   <li>При встрече закрывающего тега вызывается {@link #build()},
 *       возвращающий готовый DTO (Record)</li>
 * </ol>
 */
public interface NodeBuilder {

    /**
     * Добавляет текстовый фрагмент.
     * Текст может приходить кусками (StAX разбивает CDATA, длинные строки),
     * билдер должен их корректно склеивать.
     */
    void appendText(String text);

    /**
     * Добавляет дочерний узел (результат {@link #build()} вложенного билдера).
     */
    void addChild(Object childNode);

    /**
     * Строит и возвращает готовый DTO-объект.
     * После вызова билдер считается завершённым.
     *
     * @return DTO-объект (Paragraph, Strong, Link, etc.) или null для IgnoreBuilder
     */
    Object build();
}