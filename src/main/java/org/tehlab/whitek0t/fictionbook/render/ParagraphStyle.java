package org.tehlab.whitek0t.fictionbook.render;

/**
 * Стили параграфов в FB2/FB3.
 * <p>
 * В FB2 стиль определяется контекстом (где находится {@code <p>}):
 * - Обычный {@code <p>} в {@code <section>} → NORMAL
 * - {@code <p>} внутри {@code <title>} → SECTION_TITLE
 * - {@code <p>} внутри {@code <cite>} → CITATION
 * - {@code <p>} внутри {@code <epigraph>} → EPIGRAPH
 * - и т.д.
 * <p>
 * Рендерер использует этот enum для применения соответствующего форматирования
 * (CSS-классы, отступы, шрифты).
 */
public enum ParagraphStyle {

    /**
     * Обычный параграф текста.
     * Пример: {@code <p>Текст главы</p>}
     */
    NORMAL("normal", "paragraph"),

    /**
     * Заголовок секции (главы).
     * Пример: {@code <title><p>Глава 1</p></title>}
     */
    SECTION_TITLE("section-title", "section-title"),

    /**
     * Подзаголовок.
     * Пример: {@code <subtitle>Часть первая</subtitle>}
     */
    SUBTITLE("subtitle", "subtitle"),

    /**
     * Цитата.
     * Пример: {@code <cite><p>Текст цитаты</p></cite>}
     */
    CITATION("citation", "citation"),

    /**
     * Автор цитаты или эпиграфа.
     * Пример: {@code <text-author>А.С. Пушкин</text-author>}
     */
    TEXT_AUTHOR("text-author", "text-author"),

    /**
     * Эпиграф.
     * Пример: {@code <epigraph><p>Текст эпиграфа</p></epigraph>}
     */
    EPIGRAPH("epigraph", "epigraph"),

    /**
     * Стих (строка стихотворения).
     * Пример: {@code <v>Строка стиха</v>}
     */
    VERSE("verse", "verse"),

    /**
     * Автор стихотворения.
     * Пример: {@code <poem><text-author>Поэт</text-author></poem>}
     */
    POEM_AUTHOR("poem-author", "poem-author"),

    /**
     * Дата (часто встречается в письмах, дневниках).
     * Пример: {@code <date>15 мая 1890</date>}
     */
    DATE("date", "date"),

    /**
     * Аннотация книги.
     * Пример: {@code <annotation><p>Краткое описание</p></annotation>}
     */
    ANNOTATION("annotation", "annotation"),

    /**
     * Подпись к изображению.
     * Не является стандартным FB2-тегом, но часто встречается в расширениях.
     */
    IMAGE_CAPTION("image-caption", "image-caption"),

    /**
     * Примечание (сноска).
     * Параграфы внутри {@code <body name="notes">}
     */
    NOTE("note", "note");

    private final String name;
    private final String cssClass;

    ParagraphStyle(String name, String cssClass) {
        this.name = name;
        this.cssClass = cssClass;
    }

    /**
     * @return Имя стиля для логирования и отладки
     */
    public String getName() {
        return name;
    }

    /**
     * @return CSS-класс для HTML-рендеринга
     */
    public String getCssClass() {
        return cssClass;
    }

    /**
     * @return true, если этот стиль должен иметь увеличенный размер шрифта
     */
    public boolean isLargeFont() {
        return this == SECTION_TITLE || this == SUBTITLE;
    }

    /**
     * @return true, если этот стиль должен быть выделен (жирный, курсив)
     */
    public boolean isEmphasized() {
        return this == SECTION_TITLE || this == SUBTITLE || this == CITATION;
    }

    /**
     * @return true, если этот стиль должен иметь отступ слева
     */
    public boolean hasIndent() {
        return this == CITATION || this == EPIGRAPH || this == VERSE;
    }

    /**
     * @return true, если этот стиль должен быть выровнен по центру
     */
    public boolean isCentered() {
        return this == SECTION_TITLE || this == SUBTITLE || this == POEM_AUTHOR;
    }

    /**
     * Определяет стиль параграфа по его контексту в FB2-дереве.
     * Вызывается парсером при создании ParagraphDto.
     *
     * @param parentTags стек родительских тегов (от корня к текущему)
     * @return определённый стиль
     */
    public static ParagraphStyle fromContext(java.util.Deque<String> parentTags) {
        if (parentTags == null || parentTags.isEmpty()) {
            return NORMAL;
        }

        // Проверяем ближайшие родительские теги
        for (String tag : parentTags) {
            switch (tag) {
                case "title":
                    return SECTION_TITLE;
                case "subtitle":
                    return SUBTITLE;
                case "cite":
                    return CITATION;
                case "text-author":
                    // Если text-author внутри poem → POEM_AUTHOR, иначе TEXT_AUTHOR
                    if (parentTags.stream().anyMatch(t -> t.equals("poem"))) {
                        return POEM_AUTHOR;
                    }
                    return TEXT_AUTHOR;
                case "epigraph":
                    return EPIGRAPH;
                case "v":
                    return VERSE;
                case "date":
                    return DATE;
                case "annotation":
                    return ANNOTATION;
            }
        }

        return NORMAL;
    }
}