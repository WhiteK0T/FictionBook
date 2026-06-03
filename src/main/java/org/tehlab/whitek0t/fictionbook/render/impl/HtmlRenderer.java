package org.tehlab.whitek0t.fictionbook.render.impl;

import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.render.FictionBookRenderer;
import org.tehlab.whitek0t.fictionbook.render.ParagraphStyle;
import org.tehlab.whitek0t.fictionbook.render.ResourceResolver;

import java.util.Objects;

/**
 * Рендерит книгу в HTML.
 *
 * <p>Особенности:</p>
 * <ul>
 *   <li>Генерирует семантический HTML5</li>
 *   <li>Использует CSS-классы из {@link ParagraphStyle}</li>
 *   <li>Поддерживает кастомный {@link ResourceResolver} для картинок</li>
 *   <li>Экранирует HTML-спецсимволы</li>
 * </ul>
 *
 * <p>Пример использования:</p>
 * <pre>{@code
 * FictionBookDto book = FictionBookIO.read(file);
 *
 * HtmlRenderer renderer = HtmlRenderer.builder()
 *     .resourceResolver(ResourceResolver.base64DataUri())
 *     .wrapInHtmlDocument(true)
 *     .title(book.description().titleInfo().bookTitle())
 *     .build();
 *
 * BookPlayer player = new BookPlayer(renderer,
 *     href -> lookupResource(book, href));
 * player.play(book);
 *
 * String html = renderer.getOutput();
 * Files.writeString(Path.of("book.html"), html);
 * }</pre>
 */
public class HtmlRenderer implements FictionBookRenderer {

    private final StringBuilder html = new StringBuilder();
    private final ResourceResolver resourceResolver;
    private final boolean wrapInDocument;
    private final String documentTitle;
    private final String customCss;

    private boolean documentStarted = false;

    private HtmlRenderer(Builder builder) {
        this.resourceResolver = Objects.requireNonNullElse(builder.resourceResolver,
                ResourceResolver.placeholder());
        this.wrapInDocument = builder.wrapInDocument;
        this.documentTitle = builder.title;
        this.customCss = builder.customCss;
    }

    /**
     * Создаёт builder для конфигурирования рендерера.
     *
     * @return новый {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Создаёт рендерер с настройками по умолчанию.
     */
    public HtmlRenderer() {
        this(builder());
    }

    // ========================================================================
    // INLINE СТИЛИ
    // ========================================================================

    @Override
    public void startBold() {
        html.append("<strong>");
    }

    @Override
    public void endBold() {
        html.append("</strong>");
    }

    @Override
    public void startItalic() {
        html.append("<em>");
    }

    @Override
    public void endItalic() {
        html.append("</em>");
    }

    @Override
    public void startStrikethrough() {
        html.append("<s>");
    }

    @Override
    public void endStrikethrough() {
        html.append("</s>");
    }

    @Override
    public void startSub() {
        html.append("<sub>");
    }

    @Override
    public void endSub() {
        html.append("</sub>");
    }

    @Override
    public void startSup() {
        html.append("<sup>");
    }

    @Override
    public void endSup() {
        html.append("</sup>");
    }

    // ========================================================================
    // БЛОЧНЫЕ ЭЛЕМЕНТЫ
    // ========================================================================

    @Override
    public void startSection(String id) {
        ensureDocumentStarted();
        html.append("<section");
        if (id != null && !id.isBlank()) {
            html.append(" id=\"").append(escapeAttr(id)).append("\"");
        }
        html.append(">\n");
    }

    @Override
    public void endSection() {
        html.append("</section>\n");
    }

    @Override
    public void startParagraph(ParagraphStyle style) {
        html.append("<p");

        if (style != null && style != ParagraphStyle.NORMAL) {
            html.append(" class=\"").append(escapeAttr(style.getCssClass())).append("\"");
        }

        // Inline-стили для особых случаев
        if (style != null) {
            StringBuilder inlineStyle = new StringBuilder();
            if (style.isCentered()) {
                inlineStyle.append("text-align:center;");
            }
            if (style.hasIndent()) {
                inlineStyle.append("margin-left:2em;");
            }
            if (!inlineStyle.isEmpty()) {
                html.append(" style=\"").append(escapeAttr(inlineStyle.toString())).append("\"");
            }
        }

        html.append(">");
    }

    @Override
    public void endParagraph() {
        html.append("</p>\n");
    }

    @Override
    public void startVerse() {
        html.append("<div class=\"verse\">");
    }

    @Override
    public void endVerse() {
        html.append("</div>\n");
    }

    @Override
    public void startStanza() {
        html.append("<div class=\"stanza\">\n");
    }

    @Override
    public void endStanza() {
        html.append("</div>\n");
    }

    @Override
    public void emptyLine() {
        html.append("<div class=\"empty-line\"></div>\n");
    }

    // ========================================================================
    // ТАБЛИЦЫ
    // ========================================================================

    @Override
    public void startTable() {
        html.append("<table>\n");
    }

    @Override
    public void endTable() {
        html.append("</table>\n");
    }

    @Override
    public void startTableRow() {
        html.append("<tr>\n");
    }

    @Override
    public void endTableRow() {
        html.append("</tr>\n");
    }

    @Override
    public void startTableCell() {
        html.append("<td>");
    }

    @Override
    public void endTableCell() {
        html.append("</td>\n");
    }

    // ========================================================================
    // КОНТЕНТ
    // ========================================================================

    @Override
    public void text(String value) {
        if (value != null) {
            html.append(escapeHtml(value));
        }
    }

    @Override
    public void image(Resource resource, String alt) {
        String src = resourceResolver.resolve(resource, alt);

        html.append("<img");
        if (src != null) {
            html.append(" src=\"").append(escapeAttr(src)).append("\"");
        }
        if (alt != null && !alt.isBlank()) {
            html.append(" alt=\"").append(escapeAttr(alt)).append("\"");
        } else {
            html.append(" alt=\"\"");
        }
        html.append("/>");
    }

    @Override
    public void startLink(String href) {
        html.append("<a");
        if (href != null && !href.isBlank()) {
            html.append(" href=\"").append(escapeAttr(href)).append("\"");

            // Внешние ссылки открываем в новой вкладке
            if (isExternalLink(href)) {
                html.append(" target=\"_blank\" rel=\"noopener noreferrer\"");
            }
        }
        html.append(">");
    }

    @Override
    public void endLink() {
        html.append("</a>");
    }

    // ========================================================================
    // РЕЗУЛЬТАТ
    // ========================================================================

    /**
     * Возвращает сгенерированный HTML.
     *
     * @return HTML-строка (полный документ или фрагмент — в зависимости от настроек)
     */
    public String getOutput() {
        if (wrapInDocument && !documentStarted) {
            // Если документ не начался (книга пустая), всё равно вернём обёртку
            StringBuilder result = new StringBuilder();
            appendDocumentHeader(result);
            appendDocumentFooter(result);
            return result.toString();
        }

        if (wrapInDocument) {
            StringBuilder result = new StringBuilder();
            // Header уже был добавлен при ensureDocumentStarted
            result.append(html);
            appendDocumentFooter(result);
            return result.toString();
        }

        return html.toString();
    }

    // ========================================================================
    // ВНУТРЕННИЕ МЕТОДЫ
    // ========================================================================

    private void ensureDocumentStarted() {
        if (wrapInDocument && !documentStarted) {
            appendDocumentHeader(html);
            documentStarted = true;
        }
    }

    private void appendDocumentHeader(StringBuilder sb) {
        sb.append("<!DOCTYPE html>\n")
                .append("<html lang=\"ru\">\n")
                .append("<head>\n")
                .append("  <meta charset=\"UTF-8\">\n")
                .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("  <title>").append(escapeHtml(documentTitle != null ? documentTitle : "Book"))
                .append("</title>\n")
                .append("  <style>\n")
                .append(getDefaultCss())
                .append("  </style>\n");

        if (customCss != null && !customCss.isBlank()) {
            sb.append("  <style>\n").append(customCss).append("  </style>\n");
        }

        sb.append("</head>\n")
                .append("<body>\n")
                .append("<article class=\"fictionbook\">\n");
    }

    private void appendDocumentFooter(StringBuilder sb) {
        sb.append("</article>\n")
                .append("</body>\n")
                .append("</html>\n");
    }

    private String getDefaultCss() {
        return """
            body {
                font-family: Georgia, 'Times New Roman', serif;
                line-height: 1.6;
                max-width: 800px;
                margin: 0 auto;
                padding: 2em;
                color: #333;
                background: #fff;
            }
            .fictionbook { }
            section { margin-bottom: 2em; }
            p { text-indent: 1.5em; margin: 0.3em 0; }
            .section-title {
                font-size: 1.8em;
                font-weight: bold;
                text-align: center;
                text-indent: 0;
                margin: 1.5em 0 1em 0;
            }
            .subtitle {
                font-size: 1.3em;
                font-weight: bold;
                text-align: center;
                text-indent: 0;
                margin: 1em 0;
            }
            .citation {
                margin-left: 2em;
                margin-right: 1em;
                font-style: italic;
                text-indent: 0;
            }
            .text-author {
                text-align: right;
                text-indent: 0;
                margin-top: 0.5em;
            }
            .epigraph {
                margin-left: 3em;
                margin-right: 2em;
                font-style: italic;
                text-indent: 0;
            }
            .stanza {
                margin: 1em 0 1em 2em;
            }
            .verse {
                text-indent: 0;
                white-space: pre-line;
            }
            .poem-author {
                text-align: right;
                font-style: italic;
                text-indent: 0;
            }
            .date {
                text-align: center;
                font-style: italic;
                text-indent: 0;
            }
            .annotation {
                font-size: 0.95em;
                color: #555;
                margin: 1em 2em;
                text-indent: 0;
            }
            .note {
                font-size: 0.9em;
                color: #666;
            }
            .empty-line {
                height: 1em;
            }
            table {
                border-collapse: collapse;
                margin: 1em 0;
            }
            td {
                border: 1px solid #ccc;
                padding: 0.5em;
                vertical-align: top;
            }
            a { color: #0066cc; text-decoration: none; }
            a:hover { text-decoration: underline; }
            img { max-width: 100%; height: auto; display: block; margin: 1em auto; }
            """;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"'  -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private String escapeAttr(String text) {
        if (text == null) return "";
        return escapeHtml(text).replace("\"", "&quot;").replace("'", "&#39;");
    }

    private boolean isExternalLink(String href) {
        return href.startsWith("http://") || href.startsWith("https://")
                || href.startsWith("mailto:");
    }

    // ========================================================================
    // BUILDER
    // ========================================================================

    /** Builder для конфигурирования {@link HtmlRenderer}. */
    public static class Builder {
        private ResourceResolver resourceResolver;
        private boolean wrapInDocument = false;
        private String title;
        private String customCss;

        /** Создаёт builder со значениями по умолчанию. */
        public Builder() {
        }

        /**
         * Задаёт резолвер картинок.
         *
         * @param resolver резолвер ресурсов
         * @return этот же builder (fluent API)
         */
        public Builder resourceResolver(ResourceResolver resolver) {
            this.resourceResolver = resolver;
            return this;
        }

        /**
         * Оборачивать ли результат в полный HTML-документ (DOCTYPE, head, body).
         * Если {@code false} — возвращается только HTML-фрагмент.
         *
         * @param wrap {@code true} — полный документ, {@code false} — фрагмент
         * @return этот же builder (fluent API)
         */
        public Builder wrapInHtmlDocument(boolean wrap) {
            this.wrapInDocument = wrap;
            return this;
        }

        /**
         * Задаёт заголовок HTML-документа (тег {@code <title>}).
         *
         * @param title текст заголовка
         * @return этот же builder (fluent API)
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * Добавляет пользовательский CSS (после стандартного).
         *
         * @param css дополнительный CSS
         * @return этот же builder (fluent API)
         */
        public Builder customCss(String css) {
            this.customCss = css;
            return this;
        }

        /**
         * Собирает настроенный рендерер.
         *
         * @return новый {@link HtmlRenderer}
         */
        public HtmlRenderer build() {
            return new HtmlRenderer(this);
        }
    }
}
