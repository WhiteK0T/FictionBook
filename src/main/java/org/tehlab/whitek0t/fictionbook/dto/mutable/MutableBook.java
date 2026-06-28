package org.tehlab.whitek0t.fictionbook.dto.mutable;

import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Изменяемая («mutable») модель книги — удобный слой для редактирования поверх
 * иммутабельного {@link FictionBookDto}.
 *
 * <p>DTO построен на Java Records и неизменяем: любая правка дерева требует его
 * полного пересоздания (см. {@code FictionBookDtoTransformer}). Это безопасно, но
 * неудобно, когда нужно просто «добавить главу», «удалить секцию по id» или
 * «переставить абзацы». Эта модель решает именно такую задачу:</p>
 *
 * <pre>{@code
 * MutableBook book = MutableBook.from(FictionBookIO.read(path));
 *
 * book.mainBody().addSection(
 *     MutableSection.withTitle("Глава 1")
 *         .addParagraph("Once upon a time…")
 *         .addEmptyLine());
 *
 * book.removeSection("draft-chapter");      // удалить по id на любой глубине
 *
 * FictionBookDto edited = book.toDto();     // обратно в иммутабельный DTO
 * FictionBookIO.write(edited, out);
 * }</pre>
 *
 * <h2>Что изменяемо, а что нет</h2>
 * <p>Изменяем «структурный хребет» книги — {@code MutableBook → }
 * {@link MutableBody}{@code  → }{@link MutableSection}: именно его больно править
 * вручную. Содержимое секций ({@link org.tehlab.whitek0t.fictionbook.dto.block.BlockElement
 * BlockElement}: параграфы, стихи, таблицы…) и inline-форматирование остаются
 * иммутабельными записями — их кладут и достают из «живых» списков целиком. Так
 * не появляется второй параллельной иерархии классов, которую пришлось бы
 * синхронизировать с DTO при каждом его изменении.</p>
 *
 * <p>{@link #description()} и {@link #resources()} переносятся как иммутабельные
 * значения: описание заменяется целиком ({@link #description(Description)}),
 * бинарные ресурсы кладутся/удаляются в «живой» карте {@link #resources()}.</p>
 *
 * <p><b>Round-trip.</b> {@code MutableBook.from(dto).toDto()} структурно равен
 * исходному {@code dto} (порядок ресурсов сохраняется). Модель не потокобезопасна.</p>
 */
public final class MutableBook {

    private Description description;
    private final List<MutableBody> bodies = new ArrayList<>();
    private final Map<String, Resource> resources = new LinkedHashMap<>();

    /** Создаёт пустую книгу без описания, тел и ресурсов. */
    public MutableBook() {
    }

    /**
     * Строит изменяемую модель из иммутабельного DTO (глубокая копия «хребта»).
     *
     * @param dto исходный DTO; не может быть {@code null}
     * @return новая изменяемая книга, зеркалирующая {@code dto}
     */
    public static MutableBook from(FictionBookDto dto) {
        Objects.requireNonNull(dto, "dto must not be null");
        MutableBook book = new MutableBook();
        book.description = dto.description();
        if (dto.bodies() != null) {
            for (BodyDto body : dto.bodies()) {
                book.bodies.add(MutableBody.from(body));
            }
        }
        if (dto.resources() != null) {
            book.resources.putAll(dto.resources());
        }
        return book;
    }

    /**
     * Собирает иммутабельный DTO из текущего состояния модели.
     *
     * @return новый {@link FictionBookDto}
     */
    public FictionBookDto toDto() {
        List<BodyDto> bodyDtos = new ArrayList<>(bodies.size());
        for (MutableBody body : bodies) {
            bodyDtos.add(body.toDto());
        }
        return new FictionBookDto(description, bodyDtos, new LinkedHashMap<>(resources));
    }

    // ------------------------------------------------------------------
    // Описание и ресурсы
    // ------------------------------------------------------------------

    /**
     * Метаданные книги ({@code <description>}).
     *
     * @return текущее описание или {@code null}, если не задано
     */
    public Description description() {
        return description;
    }

    /**
     * Заменяет метаданные книги целиком.
     *
     * @param description новое описание (может быть {@code null})
     * @return этот же объект (fluent API)
     */
    public MutableBook description(Description description) {
        this.description = description;
        return this;
    }

    /**
     * «Живая» карта бинарных ресурсов ({@code <binary>}) по их id; правки видны в
     * {@link #toDto()}. Порядок вставки сохраняется ({@link LinkedHashMap}).
     *
     * @return изменяемая карта ресурсов
     */
    public Map<String, Resource> resources() {
        return resources;
    }

    /**
     * Кладёт (или заменяет) ресурс по его {@link Resource#id()}.
     *
     * @param resource ресурс; не может быть {@code null}
     * @return этот же объект (fluent API)
     */
    public MutableBook putResource(Resource resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        resources.put(resource.id(), resource);
        return this;
    }

    // ------------------------------------------------------------------
    // Тела
    // ------------------------------------------------------------------

    /**
     * «Живой» список тел книги ({@code <body>}); правки видны в {@link #toDto()}.
     *
     * @return изменяемый список тел
     */
    public List<MutableBody> bodies() {
        return bodies;
    }

    /**
     * Тело по индексу.
     *
     * @param index индекс тела
     * @return тело
     * @throws IndexOutOfBoundsException если индекс вне диапазона
     */
    public MutableBody body(int index) {
        return bodies.get(index);
    }

    /**
     * Основное тело книги (первое неименованное или просто первое).
     *
     * <p>Если книга пуста — создаёт и добавляет новое основное тело, чтобы вызовы
     * вида {@code book.mainBody().addSection(...)} работали и на пустой книге.</p>
     *
     * @return основное тело (никогда не {@code null})
     */
    public MutableBody mainBody() {
        for (MutableBody body : bodies) {
            if (body.name() == null) {
                return body;
            }
        }
        if (!bodies.isEmpty()) {
            return bodies.getFirst();
        }
        MutableBody main = new MutableBody();
        bodies.add(main);
        return main;
    }

    /**
     * Тело сносок ({@code <body name="notes">}), если есть.
     *
     * @return тело с именем {@code "notes"} или {@code null}
     */
    public MutableBody notesBody() {
        for (MutableBody body : bodies) {
            if ("notes".equals(body.name())) {
                return body;
            }
        }
        return null;
    }

    /**
     * Добавляет тело в конец книги.
     *
     * @param body тело; не может быть {@code null}
     * @return этот же объект (fluent API)
     */
    public MutableBook addBody(MutableBody body) {
        bodies.add(Objects.requireNonNull(body, "body must not be null"));
        return this;
    }

    // ------------------------------------------------------------------
    // Поиск/удаление секций по id (по всему дереву)
    // ------------------------------------------------------------------

    /**
     * Ищет первую секцию с указанным {@code id} во всех телах, рекурсивно обходя
     * подсекции.
     *
     * @param id искомый якорь секции
     * @return найденная секция или {@link Optional#empty()}
     */
    public Optional<MutableSection> findSection(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (MutableBody body : bodies) {
            Optional<MutableSection> found = MutableSection.findIn(body.sections(), id);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Удаляет первую секцию с указанным {@code id} из её родительского списка
     * (на любой глубине, во всех телах).
     *
     * @param id якорь удаляемой секции
     * @return {@code true}, если секция найдена и удалена
     */
    public boolean removeSection(String id) {
        if (id == null) {
            return false;
        }
        for (MutableBody body : bodies) {
            if (MutableSection.removeFrom(body.sections(), id)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Фабрики inline/блоков для удобства
    // ------------------------------------------------------------------

    /**
     * Создаёт параграф из простого текста (один {@link Text}-узел).
     *
     * @param text текст параграфа (может быть {@code null} → пустой параграф)
     * @return параграф
     */
    public static Paragraph paragraph(String text) {
        List<InlineElement> elements = new ArrayList<>(1);
        if (text != null) {
            elements.add(new Text(text));
        }
        return new Paragraph(elements);
    }
}
