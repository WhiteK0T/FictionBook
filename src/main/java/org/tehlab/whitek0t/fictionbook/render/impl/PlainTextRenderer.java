package org.tehlab.whitek0t.fictionbook.render.impl;

import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.render.FictionBookRenderer;
import org.tehlab.whitek0t.fictionbook.render.ParagraphStyle;

/**
 * Рендерит книгу в plain text.
 *
 * <p>Назначение:</p>
 * <ul>
 *   <li>Индексация для полнотекстового поиска</li>
 *   <li>Генерация аннотаций/превью</li>
 *   <li>Экспорт в текстовые форматы</li>
 *   <li>Подсчёт слов, статистика</li>
 * </ul>
 *
 * <p>Игнорирует всё форматирование (bold, italic, etc.), сохраняя только
 * структуру (параграфы, стихи) и текстовое содержимое.</p>
 *
 * <p>Пример использования:</p>
 * <pre>{@code
 * FictionBookDto book = FictionBookIO.read(file);
 *
 * PlainTextRenderer renderer = new PlainTextRenderer();
 * BookPlayer player = new BookPlayer(renderer);
 * player.play(book);
 *
 * String text = renderer.getOutput();
 * // Индексируем в Elasticsearch, считаем слова, etc.
 * }</pre>
 */
public class PlainTextRenderer implements FictionBookRenderer {

    private final StringBuilder text = new StringBuilder();
    private final boolean includeImageAlt;
    private final boolean includeSectionTitles;
    private final String lineSeparator;

    /**
     * Создаёт рендерер с настройками по умолчанию.
     */
    public PlainTextRenderer() {
        this(true, true, System.lineSeparator());
    }

    /**
     * Создаёт рендерер с кастомными настройками.
     *
     * @param includeImageAlt       включать ли alt-текст картинок
     * @param includeSectionTitles  включать ли заголовки секций
     * @param lineSeparator         разделитель строк ("\n" или System.lineSeparator())
     */
    public PlainTextRenderer(boolean includeImageAlt, boolean includeSectionTitles,
                             String lineSeparator) {
        this.includeImageAlt = includeImageAlt;
        this.includeSectionTitles = includeSectionTitles;
        this.lineSeparator = lineSeparator;
    }

    // ========================================================================
    // INLINE СТИЛИ (игнорируются)
    // ========================================================================

    @Override
    public void startBold() { /* no-op */ }

    @Override
    public void endBold() { /* no-op */ }

    @Override
    public void startItalic() { /* no-op */ }

    @Override
    public void endItalic() { /* no-op */ }

    @Override
    public void startStrikethrough() { /* no-op */ }

    @Override
    public void endStrikethrough() { /* no-op */ }

    @Override
    public void startSub() { /* no-op */ }

    @Override
    public void endSub() { /* no-op */ }

    @Override
    public void startSup() { /* no-op */ }

    @Override
    public void endSup() { /* no-op */ }

    // ========================================================================
    // БЛОЧНЫЕ ЭЛЕМЕНТЫ
    // ========================================================================

    @Override
    public void startSection(String id) {
        // Можно добавить разделитель между главами
        if (!text.isEmpty()) {
            text.append(lineSeparator).append(lineSeparator);
        }
    }

    @Override
    public void endSection() {
        // no-op
    }

    @Override
    public void startParagraph(ParagraphStyle style) {
        // Для заголовков можно добавить особое форматирование
        if (style != null) {
            switch (style) {
                case SECTION_TITLE, SUBTITLE -> {
                    if (!includeSectionTitles) {
                        // Пропускаем — но всё равно надо принять текст
                        // (он просто попадёт в общий поток)
                    }
                }
                case VERSE -> {
                    // Отступ для стихов
                    text.append("    ");
                }
                case CITATION, EPIGRAPH -> {
                    // Отступ для цитат
                    text.append("  ");
                }
                default -> { /* no-op */ }
            }
        }
    }

    @Override
    public void endParagraph() {
        text.append(lineSeparator);
        text.append(lineSeparator);
    }

    @Override
    public void startVerse() {
        // no-op
    }

    @Override
    public void endVerse() {
        text.append(lineSeparator);
    }

    @Override
    public void startStanza() {
        // no-op
    }

    @Override
    public void endStanza() {
        text.append(lineSeparator);
    }

    @Override
    public void emptyLine() {
        text.append(lineSeparator);
    }

    // ========================================================================
    // ТАБЛИЦЫ
    // ========================================================================

    @Override
    public void startTable() {
        text.append(lineSeparator);
    }

    @Override
    public void endTable() {
        text.append(lineSeparator);
    }

    @Override
    public void startTableRow() {
        // no-op
    }

    @Override
    public void endTableRow() {
        text.append(lineSeparator);
    }

    @Override
    public void startTableCell() {
        text.append("| ");
    }

    @Override
    public void endTableCell() {
        text.append(" ");
    }

    // ========================================================================
    // КОНТЕНТ
    // ========================================================================

    @Override
    public void text(String value) {
        if (value != null) {
            text.append(value);
        }
    }

    @Override
    public void image(Resource resource, String alt) {
        if (includeImageAlt && alt != null && !alt.isBlank()) {
            text.append("[").append(alt).append("]");
        }
    }

    @Override
    public void startLink(String href) {
        // no-op — просто выводим текст ссылки
    }

    @Override
    public void endLink() {
        // no-op
    }

    // ========================================================================
    // РЕЗУЛЬТАТ
    // ========================================================================

    /**
     * Возвращает сгенерированный текст.
     * Нормализует множественные пустые строки.
     */
    public String getOutput() {
        return normalize(text.toString());
    }

    /**
     * Возвращает количество слов в тексте.
     */
    public int getWordCount() {
        String output = getOutput();
        if (output.isBlank()) return 0;

        return (int) java.util.Arrays.stream(output.split("\\s+"))
                .filter(s -> !s.isEmpty())
                .count();
    }

    /**
     * Возвращает количество символов (без пробелов).
     */
    public int getCharacterCount() {
        String output = getOutput();
        return (int) output.chars()
                .filter(c -> !Character.isWhitespace(c))
                .count();
    }

    /**
     * Возвращает первые N символов текста (для превью).
     */
    public String getPreview(int maxLength) {
        String output = getOutput().trim();
        if (output.length() <= maxLength) {
            return output;
        }

        // Обрезаем на границе слова
        int cutPoint = output.lastIndexOf(' ', maxLength);
        if (cutPoint < maxLength / 2) {
            cutPoint = maxLength;
        }

        return output.substring(0, cutPoint).trim() + "...";
    }

    // ========================================================================
    // ВНУТРЕННИЕ МЕТОДЫ
    // ========================================================================

    /**
     * Нормализует текст: убирает лишние пустые строки, пробелы в начале/конце.
     */
    private String normalize(String input) {
        if (input == null || input.isEmpty()) return "";

        // Заменяем 3+ переносов строк на 2
        String normalized = input.replaceAll("(" + lineSeparator + "){3,}",
                lineSeparator + lineSeparator);

        // Убираем пробелы в конце каждой строки
        normalized = java.util.Arrays.stream(normalized.split(lineSeparator, -1))
                .map(String::stripTrailing)
                .collect(java.util.stream.Collectors.joining(lineSeparator));

        return normalized.strip();
    }
}
