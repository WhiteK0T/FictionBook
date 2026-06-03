package org.tehlab.whitek0t.fictionbook.render;

import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.*;
import org.tehlab.whitek0t.fictionbook.dto.inline.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Проигрыватель книги на рендерер.
 * Инкапсулирует всю логику обхода дерева и вызова соответствующих методов рендерера.
 * <p>
 * Использование:
 * <pre>
 * FictionBookRenderer renderer = new HtmlRenderer();
 * BookPlayer player = new BookPlayer(renderer);
 * player.play(book);
 * String html = renderer.getOutput();
 * </pre>
 */
public class BookPlayer {

    private final FictionBookRenderer renderer;
    private final ResourceLookup resourceLookup;
    private final Deque<String> contextStack = new ArrayDeque<>(); // стек контекста

    /**
     * Создаёт проигрыватель с рендерером.
     *
     * @param renderer реализация рендерера (HTML, PlainText, Java2D, etc.)
     */
    public BookPlayer(FictionBookRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer must not be null");
        this.resourceLookup = null;
    }

    /**
     * Создаёт проигрыватель с рендерером и функцией поиска ресурсов.
     *
     * @param renderer       реализация рендерера
     * @param resourceLookup функция для поиска Resource по href (например, "#cover")
     */
    public BookPlayer(FictionBookRenderer renderer, ResourceLookup resourceLookup) {
        this.renderer = Objects.requireNonNull(renderer, "renderer must not be null");
        this.resourceLookup = resourceLookup;
    }

    /**
     * Функция поиска бинарного ресурса по ссылке при рендеринге картинок
     * (например, {@code <image l:href="#cover"/>} → {@link Resource}).
     */
    @FunctionalInterface
    public interface ResourceLookup {
        /**
         * Находит ресурс по ссылке.
         *
         * @param href ссылка на ресурс (как правило, якорь вида {@code "#cover"})
         * @return найденный ресурс или {@code null}, если ресурс не найден
         */
        Resource lookup(String href);
    }

    /**
     * Проигрывает всю книгу: обходит все её тела и эмитит события в рендерер.
     *
     * @param book книга для рендеринга; не может быть {@code null}
     */
    public void play(FictionBookDto book) {
        Objects.requireNonNull(book, "book must not be null");

        if (book.bodies() == null || book.bodies().isEmpty()) {
            return;
        }

        for (BodyDto body : book.bodies()) {
            playBody(body);
        }
    }

    /**
     * Проигрывает одно тело книги (основной текст или примечания).
     *
     * @param body тело книги; {@code null} безопасно игнорируется
     */
    public void playBody(BodyDto body) {
        if (body == null || body.sections() == null) {
            return;
        }

        for (Section section : body.sections()) {
            playSection(section);
        }
    }

    /**
     * Проигрывает секцию (главу) рекурсивно: заголовок, содержимое и вложенные секции.
     *
     * @param section секция; {@code null} безопасно игнорируется
     */
    public void playSection(Section section) {
        if (section == null) {
            return;
        }

        contextStack.push("section");
        renderer.startSection(section.id());

        // Заголовок секции
        if (section.title() != null) {
            contextStack.push("title");
            for (BlockElement block : section.title()) {
                playBlock(block);
            }
            contextStack.pop();
        }

        // Содержимое секции
        if (section.content() != null) {
            for (BlockElement block : section.content()) {
                playBlock(block);
            }
        }

        // Вложенные секции
        if (section.subSections() != null) {
            for (Section sub : section.subSections()) {
                playSection(sub);
            }
        }

        renderer.endSection();
        contextStack.pop();
    }

    /**
     * Проигрывает блочный элемент, диспетчеризуя по конкретному типу через pattern matching.
     *
     * @param block блочный элемент; {@code null} безопасно игнорируется
     */
    public void playBlock(BlockElement block) {
        if (block == null) {
            return;
        }

        if (block instanceof Paragraph p) {
            playParagraph(p);
        } else if (block instanceof EmptyLine) {
            renderer.emptyLine();
        } else if (block instanceof Poem poem) {
            playPoem(poem);
        } else if (block instanceof Table table) {
            playTable(table);
        } else if (block instanceof Cite cite) {
            playCite(cite);
        } else if (block instanceof Epigraph epigraph) {
            playEpigraph(epigraph);
        } else if (block instanceof Section section) {
            playSection(section);
        }
        // Добавляйте сюда другие типы блоков по мере расширения модели
    }

    /**
     * Проигрывает параграф.
     */
    private void playParagraph(Paragraph paragraph) {
        // Определяем стиль из контекста
        ParagraphStyle style = ParagraphStyle.fromContext(contextStack);

        renderer.startParagraph(style);

        if (paragraph.elements() != null) {
            for (InlineElement inline : paragraph.elements()) {
                playInline(inline);
            }
        }

        renderer.endParagraph();
    }

    /**
     * Проигрывает стихотворение.
     */
    private void playPoem(Poem poem) {
        contextStack.push("poem");

        // Заголовок стихотворения
        if (poem.title() != null && !poem.title().isEmpty()) {
            contextStack.push("title");
            for (BlockElement block : poem.title()) {
                playBlock(block);
            }
            contextStack.pop();
        }

        // Эпиграф
        if (poem.epigraph() != null && !poem.epigraph().isEmpty()) {
            contextStack.push("epigraph");
            for (BlockElement block : poem.epigraph()) {
                playBlock(block);
            }
            contextStack.pop();
        }

        // Строфы
        if (poem.stanzas() != null) {
            for (Stanza stanza : poem.stanzas()) {
                playStanza(stanza);
            }
        }

        // Автор стихотворения
        if (poem.author() != null && !poem.author().isBlank()) {
            contextStack.push("text-author");
            renderer.startParagraph(ParagraphStyle.POEM_AUTHOR);
            renderer.text(poem.author());
            renderer.endParagraph();
            contextStack.pop();
        }

        contextStack.pop();
    }

    /**
     * Проигрывает строфу.
     */
    private void playStanza(Stanza stanza) {
        renderer.startStanza();

        if (stanza.verses() != null) {
            for (Verse verse : stanza.verses()) {
                playVerse(verse);
            }
        }

        renderer.endStanza();
    }

    /**
     * Проигрывает стих (строку стихотворения).
     */
    private void playVerse(Verse verse) {
        contextStack.push("v");
        renderer.startVerse();

        if (verse.elements() != null) {
            for (InlineElement inline : verse.elements()) {
                playInline(inline);
            }
        }

        renderer.endVerse();
        contextStack.pop();
    }

    // Новые методы для цитат и эпиграфов
    private void playCite(Cite cite) {
        contextStack.push("cite");

        if (cite.content() != null) {
            for (BlockElement block : cite.content()) {
                playBlock(block);
            }
        }

        if (cite.author() != null) {
            contextStack.push("text-author");
            renderer.startParagraph(ParagraphStyle.TEXT_AUTHOR);
            renderer.text(cite.author());
            renderer.endParagraph();
            contextStack.pop();
        }

        contextStack.pop();
    }

    private void playEpigraph(Epigraph epigraph) {
        contextStack.push("epigraph");

        if (epigraph.content() != null) {
            for (BlockElement block : epigraph.content()) {
                playBlock(block);
            }
        }

        if (epigraph.author() != null) {
            contextStack.push("text-author");
            renderer.startParagraph(ParagraphStyle.TEXT_AUTHOR);
            renderer.text(epigraph.author());
            renderer.endParagraph();
            contextStack.pop();
        }

        contextStack.pop();
    }

    /**
     * Проигрывает таблицу.
     */
    private void playTable(Table table) {
        renderer.startTable();

        if (table.rows() != null) {
            for (TableRow row : table.rows()) {
                playTableRow(row);
            }
        }

        renderer.endTable();
    }

    /**
     * Проигрывает строку таблицы.
     */
    private void playTableRow(TableRow row) {
        renderer.startTableRow();

        if (row.cells() != null) {
            for (TableCell cell : row.cells()) {
                playTableCell(cell);
            }
        }

        renderer.endTableRow();
    }

    /**
     * Проигрывает ячейку таблицы.
     */
    private void playTableCell(TableCell cell) {
        renderer.startTableCell();

        if (cell.content() != null) {
            for (BlockElement block : cell.content()) {
                playBlock(block);
            }
        }

        renderer.endTableCell();
    }

    /**
     * Проигрывает встроенный (inline) элемент, диспетчеризуя по типу через pattern matching.
     *
     * @param inline inline-элемент; {@code null} безопасно игнорируется
     */
    public void playInline(InlineElement inline) {
        if (inline == null) {
            return;
        }

        if (inline instanceof Text text) {
            playText(text);
        } else if (inline instanceof Strong strong) {
            playStrong(strong);
        } else if (inline instanceof Emphasis emphasis) {
            playEmphasis(emphasis);
        } else if (inline instanceof Strikethrough strikethrough) {
            playStrikethrough(strikethrough);
        } else if (inline instanceof Link link) {
            playLink(link);
        } else if (inline instanceof ImageRef imageRef) {
            playImageRef(imageRef);
        } else if (inline instanceof Sub sub) {
            playSub(sub);
        } else if (inline instanceof Sup sup) {
            playSup(sup);
        }
        // Добавляйте сюда другие типы инлайнов по мере расширения модели
    }

    /**
     * Проигрывает текст.
     */
    private void playText(Text text) {
        if (text.value() != null) {
            renderer.text(text.value());
        }
    }

    /**
     * Проигрывает жирный текст.
     */
    private void playStrong(Strong strong) {
        renderer.startBold();

        if (strong.elements() != null) {
            for (InlineElement inline : strong.elements()) {
                playInline(inline);
            }
        }

        renderer.endBold();
    }

    /**
     * Проигрывает курсив.
     */
    private void playEmphasis(Emphasis emphasis) {
        renderer.startItalic();

        if (emphasis.elements() != null) {
            for (InlineElement inline : emphasis.elements()) {
                playInline(inline);
            }
        }

        renderer.endItalic();
    }

    /**
     * Проигрывает зачёркнутый текст.
     */
    private void playStrikethrough(Strikethrough strikethrough) {
        renderer.startStrikethrough();

        if (strikethrough.elements() != null) {
            for (InlineElement inline : strikethrough.elements()) {
                playInline(inline);
            }
        }

        renderer.endStrikethrough();
    }

    /**
     * Проигрывает ссылку.
     */
    private void playLink(Link link) {
        renderer.startLink(link.href());

        if (link.elements() != null) {
            for (InlineElement inline : link.elements()) {
                playInline(inline);
            }
        }

        renderer.endLink();
    }

    /**
     * Проигрывает изображение.
     */
    private void playImageRef(ImageRef imageRef) {
        Resource resource = null;

        // Пытаемся найти ресурс через lookup
        if (resourceLookup != null && imageRef.href() != null) {
            resource = resourceLookup.lookup(imageRef.href());
        }

        renderer.image(resource, imageRef.alt());
    }

    /**
     * Проигрывает нижний индекс.
     */
    private void playSub(Sub sub) {
        renderer.startSub();

        if (sub.elements() != null) {
            for (InlineElement inline : sub.elements()) {
                playInline(inline);
            }
        }

        renderer.endSub();
    }

    /**
     * Проигрывает верхний индекс.
     */
    private void playSup(Sup sup) {
        renderer.startSup();

        if (sup.elements() != null) {
            for (InlineElement inline : sup.elements()) {
                playInline(inline);
            }
        }

        renderer.endSup();
    }
}