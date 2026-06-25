package org.tehlab.whitek0t.fictionbook.internal.info;

import org.tehlab.whitek0t.fictionbook.api.BookInfo;
import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;
import org.tehlab.whitek0t.fictionbook.dto.block.Cite;
import org.tehlab.whitek0t.fictionbook.dto.block.Epigraph;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.block.Poem;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.block.Stanza;
import org.tehlab.whitek0t.fictionbook.dto.block.Table;
import org.tehlab.whitek0t.fictionbook.dto.block.TableRow;
import org.tehlab.whitek0t.fictionbook.dto.block.Verse;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.dto.description.PublishInfo;
import org.tehlab.whitek0t.fictionbook.dto.description.TitleInfo;
import org.tehlab.whitek0t.fictionbook.dto.inline.Emphasis;
import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Link;
import org.tehlab.whitek0t.fictionbook.dto.inline.Strikethrough;
import org.tehlab.whitek0t.fictionbook.dto.inline.Strong;
import org.tehlab.whitek0t.fictionbook.dto.inline.Sub;
import org.tehlab.whitek0t.fictionbook.dto.inline.Sup;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Извлекает краткую сводку {@link BookInfo} из разобранного {@link FictionBookDto}:
 * берёт метаданные из {@code description}, разрешает обложку через ресурсы и
 * обходит тело книги для подсчёта символов/слов и плоского текста аннотации.
 */
public final class BookInfoExtractor {

    private BookInfoExtractor() {
    }

    /**
     * Собирает {@link BookInfo} из книги.
     *
     * @param dto      разобранная книга (не {@code null})
     * @param fileName имя исходного файла или {@code null}, если неизвестно
     * @return заполненная сводка
     */
    public static BookInfo extract(FictionBookDto dto, String fileName) {
        Description desc = dto.description();
        TitleInfo title = desc == null ? null : desc.titleInfo();
        PublishInfo pub = desc == null ? null : desc.publishInfo();

        // Текст тела: считаем символы/слова по плоскому тексту всех секций.
        String bodyText = bodiesToText(dto.bodies());
        String normBody = bodyText.replaceAll("\\s+", " ").trim();
        int charCount = normBody.length();
        int wordCount = normBody.isEmpty() ? 0 : normBody.split(" ").length;

        // Аннотация: построчно (абзацы через \n).
        String annotationText = title == null ? null : blocksToText(title.annotation());

        // Ссылки: href всех <a> из аннотации и тела, distinct в порядке появления.
        List<String> links = collectLinks(title == null ? null : title.annotation(), dto.bodies());

        return new BookInfo(
                fileName,
                title == null ? null : title.bookTitle(),
                title == null ? List.of() : nullToEmpty(title.authors()),
                title == null ? List.of() : nullToEmpty(title.genres()),
                title == null ? null : title.lang(),
                title == null ? null : title.sequence(),
                pub == null ? null : pub.year(),
                pub == null ? null : pub.publisher(),
                pub == null ? null : pub.city(),
                pub == null ? null : pub.isbn(),
                annotationText,
                title == null ? List.of() : nullToEmpty(title.annotation()),
                charCount,
                wordCount,
                resolveCover(title, dto.resources()),
                links
        );
    }

    // ========================================================================
    // ССЫЛКИ
    // ========================================================================

    /**
     * Собирает href внешних {@link Link} из аннотации и тела (distinct, порядок
     * появления). Внутренние якоря/сноски ({@code href}, начинающийся с {@code #})
     * не считаются ссылками книги и пропускаются.
     */
    private static List<String> collectLinks(List<BlockElement> annotation, List<BodyDto> bodies) {
        LinkedHashSet<String> hrefs = new LinkedHashSet<>();
        Consumer<InlineElement> visitor = e -> {
            if (e instanceof Link l && l.href() != null) {
                String href = l.href().strip();
                if (!href.isEmpty() && !href.startsWith("#")) {
                    hrefs.add(href);
                }
            }
        };
        if (annotation != null) annotation.forEach(b -> walkInlines(b, visitor));
        if (bodies != null) {
            for (BodyDto body : bodies) {
                if (body == null || body.sections() == null) continue;
                body.sections().forEach(s -> walkInlines(s, visitor));
            }
        }
        return List.copyOf(hrefs);
    }

    /** Обходит все inline-элементы внутри блока (рекурсивно по контейнерам). */
    private static void walkInlines(BlockElement block, Consumer<InlineElement> visitor) {
        switch (block) {
            case Paragraph p -> visitInlines(p.elements(), visitor);
            case Verse v -> visitInlines(v.elements(), visitor);
            case Stanza st -> {
                if (st.verses() != null) st.verses().forEach(v -> walkInlines(v, visitor));
            }
            case Cite c -> walkBlocks(c.content(), visitor);
            case Epigraph e -> walkBlocks(e.content(), visitor);
            case Poem poem -> {
                walkBlocks(poem.title(), visitor);
                walkBlocks(poem.epigraph(), visitor);
                if (poem.stanzas() != null) poem.stanzas().forEach(s -> walkInlines(s, visitor));
            }
            case Section sec -> {
                walkBlocks(sec.title(), visitor);
                walkBlocks(sec.content(), visitor);
                if (sec.subSections() != null) sec.subSections().forEach(s -> walkInlines(s, visitor));
            }
            case Table t -> {
                if (t.rows() != null) {
                    for (TableRow row : t.rows()) {
                        if (row.cells() == null) continue;
                        row.cells().forEach(cell -> walkBlocks(cell.content(), visitor));
                    }
                }
            }
            default -> { /* EmptyLine и прочее inline не содержат */ }
        }
    }

    private static void walkBlocks(List<BlockElement> blocks, Consumer<InlineElement> visitor) {
        if (blocks != null) blocks.forEach(b -> walkInlines(b, visitor));
    }

    /** Посещает inline-элементы и спускается в контейнерные (Strong, Link, …). */
    private static void visitInlines(List<InlineElement> elements, Consumer<InlineElement> visitor) {
        if (elements == null) return;
        for (InlineElement e : elements) {
            visitor.accept(e);
            switch (e) {
                case Strong s -> visitInlines(s.elements(), visitor);
                case Emphasis em -> visitInlines(em.elements(), visitor);
                case Sub sub -> visitInlines(sub.elements(), visitor);
                case Sup sup -> visitInlines(sup.elements(), visitor);
                case Strikethrough st -> visitInlines(st.elements(), visitor);
                case Link l -> visitInlines(l.elements(), visitor);
                default -> { /* Text, ImageRef — листья */ }
            }
        }
    }

    /** Находит ресурс обложки по первому валидному id из {@code coverImageIds}. */
    private static Resource resolveCover(TitleInfo title, Map<String, Resource> resources) {
        if (title == null || title.coverImageIds() == null || resources == null) {
            return null;
        }
        for (String id : title.coverImageIds()) {
            if (id == null || id.isBlank()) continue;
            String key = id.startsWith("#") ? id.substring(1) : id;
            Resource r = resources.get(key);
            if (r != null) return r;
        }
        return null;
    }

    // ========================================================================
    // ОБХОД ТЕКСТА
    // ========================================================================

    private static String bodiesToText(List<BodyDto> bodies) {
        List<String> lines = new ArrayList<>();
        if (bodies != null) {
            for (BodyDto body : bodies) {
                if (body == null || body.sections() == null) continue;
                for (Section s : body.sections()) {
                    collectLines(s, lines);
                }
            }
        }
        return String.join("\n", lines);
    }

    /** Плоский текст набора блоков: непустые абзацы/строки через {@code \n}. */
    private static String blocksToText(List<BlockElement> blocks) {
        if (blocks == null || blocks.isEmpty()) return null;
        List<String> lines = new ArrayList<>();
        for (BlockElement b : blocks) {
            collectLines(b, lines);
        }
        String text = String.join("\n", lines).strip();
        return text.isEmpty() ? null : text;
    }

    /** Рекурсивно собирает по одной строке на абзац/стих. */
    private static void collectLines(BlockElement block, List<String> out) {
        switch (block) {
            case Paragraph p -> addLine(out, inlineText(p.elements()));
            case Verse v -> addLine(out, inlineText(v.elements()));
            case Stanza st -> {
                if (st.verses() != null) st.verses().forEach(v -> collectLines(v, out));
            }
            case Cite c -> collectAll(c.content(), out);
            case Epigraph e -> collectAll(e.content(), out);
            case Poem poem -> {
                collectAll(poem.title(), out);
                collectAll(poem.epigraph(), out);
                if (poem.stanzas() != null) poem.stanzas().forEach(s -> collectLines(s, out));
            }
            case Section sec -> {
                collectAll(sec.title(), out);
                collectAll(sec.content(), out);
                if (sec.subSections() != null) sec.subSections().forEach(s -> collectLines(s, out));
            }
            case Table t -> {
                if (t.rows() != null) {
                    for (TableRow row : t.rows()) {
                        if (row.cells() == null) continue;
                        row.cells().forEach(cell -> collectAll(cell.content(), out));
                    }
                }
            }
            default -> { /* EmptyLine и прочее — текста не несут */ }
        }
    }

    private static void collectAll(List<BlockElement> blocks, List<String> out) {
        if (blocks != null) blocks.forEach(b -> collectLines(b, out));
    }

    private static void addLine(List<String> out, String line) {
        if (line != null && !line.isBlank()) out.add(line.strip());
    }

    /** Конкатенирует текст inline-элементов (картинки и их alt не учитываются). */
    private static String inlineText(List<InlineElement> elements) {
        if (elements == null) return "";
        StringBuilder sb = new StringBuilder();
        for (InlineElement e : elements) {
            switch (e) {
                case Text t -> sb.append(t.value() == null ? "" : t.value());
                case Strong s -> sb.append(inlineText(s.elements()));
                case Emphasis em -> sb.append(inlineText(em.elements()));
                case Sub sub -> sb.append(inlineText(sub.elements()));
                case Sup sup -> sb.append(inlineText(sup.elements()));
                case Strikethrough st -> sb.append(inlineText(st.elements()));
                case Link l -> sb.append(inlineText(l.elements()));
                default -> { /* ImageRef — пропускаем */ }
            }
        }
        return sb.toString();
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
