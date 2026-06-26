package org.tehlab.whitek0t.fictionbook.render.impl;

import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.render.FictionBookRenderer;
import org.tehlab.whitek0t.fictionbook.render.ParagraphStyle;
import org.tehlab.whitek0t.fictionbook.render.ResourceResolver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Рендерит книгу в Markdown (GitHub-flavored).
 *
 * <p>Сопоставление:</p>
 * <ul>
 *   <li>{@code <strong>}/{@code <emphasis>}/{@code <strikethrough>} → {@code **}/{@code *}/{@code ~~};
 *       {@code <sub>}/{@code <sup>} → inline-HTML {@code <sub>}/{@code <sup>} (GFM их рендерит)</li>
 *   <li>Заголовок секции → ATX-заголовок ({@code #}…{@code ######}) по глубине вложенности;
 *       подзаголовок — на уровень глубже</li>
 *   <li>Цитата/эпиграф/аннотация → blockquote ({@code > }); автор/дата → строка курсивом</li>
 *   <li>Стихи → строки с «жёстким» переносом (два пробела + перевод строки), строфы — пустой строкой</li>
 *   <li>Таблицы → GFM-таблицы (первая строка считается заголовком)</li>
 *   <li>Картинки → {@code ![alt](src)}; {@code src} даёт {@link ResourceResolver}
 *       (base64 data-URI / относительный путь). Без резолвера — текстовый placeholder</li>
 *   <li>Ссылки → {@code [текст](href)}</li>
 * </ul>
 *
 * <p>Спецсимволы Markdown в тексте экранируются. Картинки и ссылки идут через тот же
 * {@code BookPlayer}-механизм, что и в {@link HtmlRenderer}.</p>
 *
 * @see HtmlRenderer
 */
public class MarkdownRenderer implements FictionBookRenderer {

    private final StringBuilder md = new StringBuilder();
    private final ResourceResolver resourceResolver;
    private final Deque<String> linkHrefs = new ArrayDeque<>();

    private int sectionDepth = 0;
    private boolean inParagraph = false;
    private String paragraphSuffix = "";

    // Состояние таблицы
    private boolean inCell = false;
    private StringBuilder cell = new StringBuilder();
    private List<String> rowCells = new ArrayList<>();
    private int tableRowIndex = 0;

    /** Создаёт рендерер без резолвера картинок (картинки → текстовый placeholder). */
    public MarkdownRenderer() {
        this(null);
    }

    /**
     * Создаёт рендерер с резолвером картинок.
     *
     * @param resourceResolver стратегия получения {@code src} для {@code ![alt](src)};
     *                         {@code null} — картинки рендерятся как текстовый placeholder
     */
    public MarkdownRenderer(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    // ========================================================================
    // INLINE СТИЛИ
    // ========================================================================

    @Override
    public void startBold() {
        emit("**");
    }

    @Override
    public void endBold() {
        emit("**");
    }

    @Override
    public void startItalic() {
        emit("*");
    }

    @Override
    public void endItalic() {
        emit("*");
    }

    @Override
    public void startStrikethrough() {
        emit("~~");
    }

    @Override
    public void endStrikethrough() {
        emit("~~");
    }

    @Override
    public void startSub() {
        emit("<sub>");
    }

    @Override
    public void endSub() {
        emit("</sub>");
    }

    @Override
    public void startSup() {
        emit("<sup>");
    }

    @Override
    public void endSup() {
        emit("</sup>");
    }

    // ========================================================================
    // БЛОЧНЫЕ ЭЛЕМЕНТЫ
    // ========================================================================

    @Override
    public void startSection(String id) {
        sectionDepth++;
    }

    @Override
    public void endSection() {
        if (sectionDepth > 0) {
            sectionDepth--;
        }
    }

    @Override
    public void startParagraph(ParagraphStyle style) {
        ensureBlankLine();
        paragraphSuffix = "";

        String prefix = switch (style == null ? ParagraphStyle.NORMAL : style) {
            case SECTION_TITLE -> "#".repeat(clampHeading(sectionDepth)) + " ";
            case SUBTITLE -> "#".repeat(clampHeading(sectionDepth + 1)) + " ";
            case CITATION, EPIGRAPH, ANNOTATION -> "> ";
            case TEXT_AUTHOR, POEM_AUTHOR, DATE, IMAGE_CAPTION -> {
                paragraphSuffix = "*";
                yield "*";
            }
            default -> "";
        };

        md.append(prefix);
        inParagraph = true;
    }

    @Override
    public void endParagraph() {
        md.append(paragraphSuffix);
        paragraphSuffix = "";
        inParagraph = false;
        md.append("\n\n");
    }

    @Override
    public void startStanza() {
        ensureBlankLine();
    }

    @Override
    public void endStanza() {
        ensureBlankLine();
    }

    @Override
    public void startVerse() {
        // строка стиха начинается с чистой позиции — содержимое придёт через text()
    }

    @Override
    public void endVerse() {
        // «жёсткий» перенос Markdown: два пробела + перевод строки
        md.append("  \n");
    }

    @Override
    public void emptyLine() {
        ensureBlankLine();
    }

    // ========================================================================
    // ТАБЛИЦЫ (GFM)
    // ========================================================================

    @Override
    public void startTable() {
        ensureBlankLine();
        tableRowIndex = 0;
    }

    @Override
    public void endTable() {
        md.append("\n");
    }

    @Override
    public void startTableRow() {
        rowCells = new ArrayList<>();
    }

    @Override
    public void endTableRow() {
        md.append("| ").append(String.join(" | ", rowCells)).append(" |\n");
        if (tableRowIndex == 0) {
            // Строка-разделитель заголовка (GFM требует её после первой строки)
            md.append("|");
            md.repeat(" --- |", rowCells.size());
            md.append("\n");
        }
        tableRowIndex++;
    }

    @Override
    public void startTableCell() {
        inCell = true;
        cell = new StringBuilder();
    }

    @Override
    public void endTableCell() {
        inCell = false;
        // В ячейке переводы строк недопустимы — схлопываем в пробел
        rowCells.add(cell.toString().replace('\n', ' ').trim());
    }

    // ========================================================================
    // КОНТЕНТ
    // ========================================================================

    @Override
    public void text(String value) {
        if (value != null) {
            emit(escape(value));
        }
    }

    @Override
    public void image(Resource resource, String alt) {
        String src = resourceResolver != null ? resourceResolver.resolve(resource, alt) : null;
        String markup = imageMarkup(src, alt);

        if (inCell) {
            cell.append(markup);
        } else if (inParagraph) {
            md.append(markup);
        } else {
            // Блочная картинка (прямой ребёнок секции) — на отдельной строке
            ensureBlankLine();
            md.append(markup).append("\n\n");
        }
    }

    @Override
    public void startLink(String href) {
        linkHrefs.push(href != null ? href : "");
        emit("[");
    }

    @Override
    public void endLink() {
        String href = linkHrefs.isEmpty() ? "" : linkHrefs.pop();
        emit("](" + href + ")");
    }

    // ========================================================================
    // РЕЗУЛЬТАТ
    // ========================================================================

    /**
     * Возвращает сгенерированный Markdown (множественные пустые строки схлопнуты,
     * в конце — один перевод строки).
     *
     * @return итоговый Markdown
     */
    public String getOutput() {
        String out = md.toString().replaceAll("\n{3,}", "\n\n").strip();
        return out.isEmpty() ? "" : out + "\n";
    }

    // ========================================================================
    // ВНУТРЕННИЕ МЕТОДЫ
    // ========================================================================

    /** Пишет в активную цель: ячейку таблицы (если внутри {@code <td>}) или основной буфер. */
    private void emit(String s) {
        (inCell ? cell : md).append(s);
    }

    /** Гарантирует, что основной буфер оканчивается ровно пустой строкой (для границ блоков). */
    private void ensureBlankLine() {
        if (md.isEmpty()) {
            return;
        }
        int end = md.length();
        while (end > 0 && md.charAt(end - 1) == '\n') {
            end--;
        }
        md.setLength(end);
        md.append("\n\n");
    }

    private String imageMarkup(String src, String alt) {
        String altText = alt != null ? alt : "";
        if (src == null || src.isBlank()) {
            return altText.isBlank()
                    ? "*(изображение)*"
                    : "*(изображение: " + escapeInline(altText) + ")*";
        }
        return "![" + escapeInline(altText) + "](" + src + ")";
    }

    private static int clampHeading(int depth) {
        return Math.clamp(depth, 1, 6);
    }

    /** Экранирует спецсимволы Markdown в обычном тексте (учитывая контекст ячейки таблицы). */
    private String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\', '`', '*', '_', '[', ']', '<', '>' -> b.append('\\').append(c);
                case '|' -> b.append(inCell ? "\\|" : "|");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    /** Лёгкое экранирование для alt/подписей внутри {@code [...]}. */
    private static String escapeInline(String s) {
        return s.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
    }
}
