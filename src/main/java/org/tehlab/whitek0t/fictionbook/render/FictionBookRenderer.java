package org.tehlab.whitek0t.fictionbook.render;

import org.tehlab.whitek0t.fictionbook.dto.Resource;

/**
 * Интерфейс рендерера. Реализации: HTML, PlainText, Java2D, Swing, JavaFX...
 * Использует Command Pattern — книга "проигрывает" себя на рендерер.
 */
public interface FictionBookRenderer {

    // --- Inline стили (стековые) ---
    default void startBold() {
    }

    default void endBold() {
    }

    default void startItalic() {
    }

    default void endItalic() {
    }

    default void startStrikethrough() {
    }

    default void endStrikethrough() {
    }

    default void startSub() {
    }

    default void endSub() {
    }

    default void startSup() {
    }

    default void endSup() {
    }

    // --- Блочные элементы ---
    default void startSection(String id) {
    }

    default void endSection() {
    }

    default void startParagraph(ParagraphStyle style) {
    }

    default void endParagraph() {
    }

    default void startVerse() {
    }

    default void endVerse() {
    }

    default void startStanza() {
    }

    default void endStanza() {
    }

    default void emptyLine() {
    }

    // --- Таблицы ---
    default void startTable() {
    }

    default void endTable() {
    }

    default void startTableRow() {
    }

    default void endTableRow() {
    }

    default void startTableCell() {
    }

    default void endTableCell() {
    }

    // --- Контент ---
    default void text(String value) {
    }

    default void image(Resource resource, String alt) {
    }

    default void startLink(String href) {
    }

    default void endLink() {
    }

    // --- CSS (зарезервировано на будущее, для FB3) ---
    default void startStyledSpan(String cssClass) {
    }

    default void endStyledSpan() {
    }
}