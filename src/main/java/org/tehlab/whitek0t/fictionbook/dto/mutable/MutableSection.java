package org.tehlab.whitek0t.fictionbook.dto.mutable;

import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;
import org.tehlab.whitek0t.fictionbook.dto.block.EmptyLine;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Изменяемая секция — зеркало {@link Section} ({@code <section>}). Держит «живые»
 * списки заголовка, содержимого и подсекций, которые можно свободно дополнять,
 * чистить и переставлять.
 *
 * <p>Содержимое ({@link #content()}) и заголовок ({@link #title()}) хранят
 * иммутабельные {@link BlockElement}-записи (параграфы, стихи, таблицы…): их
 * добавляют целиком, а вложенные секции живут отдельно — в {@link #subSections()}
 * как {@code MutableSection} (рекурсивная структура).</p>
 *
 * @see MutableBook
 */
public final class MutableSection {

    private String id;
    private final List<BlockElement> title = new ArrayList<>();
    private final List<BlockElement> content = new ArrayList<>();
    private final List<MutableSection> subSections = new ArrayList<>();
    private final Map<String, String> metadata = new LinkedHashMap<>();

    /** Создаёт пустую секцию без id. */
    public MutableSection() {
    }

    /**
     * Создаёт пустую секцию с заданным id.
     *
     * @param id якорь секции (может быть {@code null})
     */
    public MutableSection(String id) {
        this.id = id;
    }

    /**
     * Создаёт секцию с простым текстовым заголовком (один параграф).
     *
     * @param titleText текст заголовка
     * @return новая секция с заголовком
     */
    public static MutableSection withTitle(String titleText) {
        return new MutableSection().titleText(titleText);
    }

    /**
     * Строит изменяемую секцию из DTO (рекурсивно зеркалит подсекции). Блоки
     * заголовка и содержимого переносятся как есть — они иммутабельны.
     *
     * @param section исходная секция; не может быть {@code null}
     * @return новая изменяемая секция
     */
    public static MutableSection from(Section section) {
        Objects.requireNonNull(section, "section must not be null");
        MutableSection result = new MutableSection(section.id());
        if (section.title() != null) {
            result.title.addAll(section.title());
        }
        if (section.content() != null) {
            result.content.addAll(section.content());
        }
        if (section.subSections() != null) {
            for (Section sub : section.subSections()) {
                result.subSections.add(from(sub));
            }
        }
        if (section.metadata() != null) {
            result.metadata.putAll(section.metadata());
        }
        return result;
    }

    /**
     * Собирает иммутабельный {@link Section}.
     *
     * @return новый DTO секции
     */
    public Section toDto() {
        List<Section> subDtos = new ArrayList<>(subSections.size());
        for (MutableSection sub : subSections) {
            subDtos.add(sub.toDto());
        }
        return new Section(
                id,
                new ArrayList<>(title),
                new ArrayList<>(content),
                subDtos,
                new LinkedHashMap<>(metadata));
    }

    // ------------------------------------------------------------------
    // Свойства
    // ------------------------------------------------------------------

    /**
     * Якорь секции.
     *
     * @return id или {@code null}
     */
    public String id() {
        return id;
    }

    /**
     * Задаёт якорь секции.
     *
     * @param id новый id (может быть {@code null})
     * @return этот же объект (fluent API)
     */
    public MutableSection id(String id) {
        this.id = id;
        return this;
    }

    /**
     * «Живой» список блоков заголовка ({@code <title>}).
     *
     * @return изменяемый список блоков заголовка
     */
    public List<BlockElement> title() {
        return title;
    }

    /**
     * «Живой» список блоков содержимого секции.
     *
     * @return изменяемый список блоков содержимого
     */
    public List<BlockElement> content() {
        return content;
    }

    /**
     * «Живой» список вложенных подсекций.
     *
     * @return изменяемый список подсекций
     */
    public List<MutableSection> subSections() {
        return subSections;
    }

    /**
     * «Живая» карта метаданных (задел под CSS-классы/lang в FB3).
     *
     * @return изменяемая карта метаданных
     */
    public Map<String, String> metadata() {
        return metadata;
    }

    // ------------------------------------------------------------------
    // Удобные мутаторы
    // ------------------------------------------------------------------

    /**
     * Заменяет заголовок секции единственным текстовым параграфом.
     *
     * @param text текст заголовка
     * @return этот же объект (fluent API)
     */
    public MutableSection titleText(String text) {
        title.clear();
        title.add(MutableBook.paragraph(text));
        return this;
    }

    /**
     * Добавляет в содержимое параграф из простого текста.
     *
     * @param text текст параграфа
     * @return этот же объект (fluent API)
     */
    public MutableSection addParagraph(String text) {
        content.add(MutableBook.paragraph(text));
        return this;
    }

    /**
     * Добавляет в содержимое готовый параграф.
     *
     * @param paragraph параграф; не может быть {@code null}
     * @return этот же объект (fluent API)
     */
    public MutableSection addParagraph(Paragraph paragraph) {
        content.add(Objects.requireNonNull(paragraph, "paragraph must not be null"));
        return this;
    }

    /**
     * Добавляет в содержимое произвольный блок.
     *
     * @param block блок; не может быть {@code null}
     * @return этот же объект (fluent API)
     */
    public MutableSection addBlock(BlockElement block) {
        content.add(Objects.requireNonNull(block, "block must not be null"));
        return this;
    }

    /**
     * Добавляет в содержимое пустую строку-отбойник ({@code <empty-line>}).
     *
     * @return этот же объект (fluent API)
     */
    public MutableSection addEmptyLine() {
        content.add(new EmptyLine());
        return this;
    }

    /**
     * Добавляет подсекцию.
     *
     * @param subSection подсекция; не может быть {@code null}
     * @return этот же объект (fluent API)
     */
    public MutableSection addSubSection(MutableSection subSection) {
        subSections.add(Objects.requireNonNull(subSection, "subSection must not be null"));
        return this;
    }

    /**
     * Ищет первую подсекцию с указанным {@code id} в этом поддереве (рекурсивно,
     * не включая саму секцию).
     *
     * @param id искомый якорь
     * @return найденная подсекция или {@link Optional#empty()}
     */
    public Optional<MutableSection> findSubSection(String id) {
        return findIn(subSections, id);
    }

    // ------------------------------------------------------------------
    // Внутренние рекурсивные помощники (используются и MutableBook)
    // ------------------------------------------------------------------

    /** Рекурсивно ищет секцию с {@code id} в списке и его подсекциях. */
    static Optional<MutableSection> findIn(List<MutableSection> sections, String id) {
        for (MutableSection section : sections) {
            if (Objects.equals(section.id, id)) {
                return Optional.of(section);
            }
            Optional<MutableSection> nested = findIn(section.subSections, id);
            if (nested.isPresent()) {
                return nested;
            }
        }
        return Optional.empty();
    }

    /** Рекурсивно удаляет первую секцию с {@code id} из её родительского списка. */
    static boolean removeFrom(List<MutableSection> sections, String id) {
        for (Iterator<MutableSection> it = sections.iterator(); it.hasNext(); ) {
            MutableSection section = it.next();
            if (Objects.equals(section.id, id)) {
                it.remove();
                return true;
            }
            if (removeFrom(section.subSections, id)) {
                return true;
            }
        }
        return false;
    }
}
