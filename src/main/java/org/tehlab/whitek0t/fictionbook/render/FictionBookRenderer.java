package org.tehlab.whitek0t.fictionbook.render;

import org.tehlab.whitek0t.fictionbook.dto.Resource;

import java.util.Map;

/**
 * Интерфейс рендерера. Реализации: HTML, PlainText, Java2D, Swing, JavaFX...
 * Использует Command Pattern — книга «проигрывает» себя на рендерер ({@link BookPlayer}),
 * вызывая события {@code start*}/{@code end*} и {@link #text(String)}.
 *
 * <p>Все методы имеют пустую реализацию по умолчанию, поэтому реализующему классу
 * достаточно переопределить лишь интересующие его события.</p>
 */
public interface FictionBookRenderer {

    // --- Inline стили (стековые) ---

    /** Начало полужирного фрагмента ({@code <strong>}). */
    default void startBold() {
    }

    /** Конец полужирного фрагмента. */
    default void endBold() {
    }

    /** Начало курсивного фрагмента ({@code <emphasis>}). */
    default void startItalic() {
    }

    /** Конец курсивного фрагмента. */
    default void endItalic() {
    }

    /** Начало зачёркнутого фрагмента ({@code <strikethrough>}). */
    default void startStrikethrough() {
    }

    /** Конец зачёркнутого фрагмента. */
    default void endStrikethrough() {
    }

    /** Начало нижнего индекса ({@code <sub>}). */
    default void startSub() {
    }

    /** Конец нижнего индекса. */
    default void endSub() {
    }

    /** Начало верхнего индекса ({@code <sup>}). */
    default void startSup() {
    }

    /** Конец верхнего индекса. */
    default void endSup() {
    }

    // --- Блочные элементы ---

    /**
     * Начало секции ({@code <section>}).
     *
     * @param id якорь секции или {@code null}
     */
    default void startSection(String id) {
    }

    /**
     * Начало секции с её атрибутами-метаданными (задел FB3 под CSS: {@code class},
     * {@code xml:lang}, {@code style}…). Это основной метод, который вызывает
     * {@link BookPlayer}; реализация по умолчанию делегирует в {@link #startSection(String)},
     * поэтому рендереры, которым метаданные не нужны, переопределяют только его.
     *
     * @param id         якорь секции или {@code null}
     * @param attributes атрибуты секции из {@code Section.metadata()} (никогда не {@code null};
     *                   может быть пустым)
     */
    default void startSection(String id, Map<String, String> attributes) {
        startSection(id);
    }

    /** Конец секции. */
    default void endSection() {
    }

    /**
     * Начало параграфа.
     *
     * @param style стиль параграфа, выведенный из контекста
     */
    default void startParagraph(ParagraphStyle style) {
    }

    /** Конец параграфа. */
    default void endParagraph() {
    }

    /** Начало стихотворной строки ({@code <v>}). */
    default void startVerse() {
    }

    /** Конец стихотворной строки. */
    default void endVerse() {
    }

    /** Начало строфы ({@code <stanza>}). */
    default void startStanza() {
    }

    /** Конец строфы. */
    default void endStanza() {
    }

    /** Пустая строка-отбойник ({@code <empty-line>}). */
    default void emptyLine() {
    }

    // --- Таблицы ---

    /** Начало таблицы ({@code <table>}). */
    default void startTable() {
    }

    /** Конец таблицы. */
    default void endTable() {
    }

    /** Начало строки таблицы ({@code <tr>}). */
    default void startTableRow() {
    }

    /** Конец строки таблицы. */
    default void endTableRow() {
    }

    /** Начало ячейки таблицы ({@code <td>}/{@code <th>}). */
    default void startTableCell() {
    }

    /** Конец ячейки таблицы. */
    default void endTableCell() {
    }

    // --- Контент ---

    /**
     * Текстовый фрагмент.
     *
     * @param value текст для вывода
     */
    default void text(String value) {
    }

    /**
     * Изображение.
     *
     * @param resource ресурс-картинка или {@code null}, если ссылка не разрешилась
     * @param alt      альтернативный текст или {@code null}
     */
    default void image(Resource resource, String alt) {
    }

    /**
     * Начало ссылки ({@code <a>}).
     *
     * @param href цель ссылки
     */
    default void startLink(String href) {
    }

    /** Конец ссылки. */
    default void endLink() {
    }

    // --- CSS (зарезервировано на будущее, для FB3) ---

    /**
     * Начало стилизованного span'а (задел под CSS в FB3).
     *
     * @param cssClass имя CSS-класса
     */
    default void startStyledSpan(String cssClass) {
    }

    /** Конец стилизованного span'а. */
    default void endStyledSpan() {
    }
}
