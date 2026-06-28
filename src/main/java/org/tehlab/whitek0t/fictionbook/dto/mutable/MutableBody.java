package org.tehlab.whitek0t.fictionbook.dto.mutable;

import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Изменяемое тело книги — зеркало {@link BodyDto} ({@code <body>}). Держит имя
 * ({@code null} для основного текста, {@code "notes"} для сносок) и «живой» список
 * секций верхнего уровня.
 *
 * @see MutableBook
 */
public final class MutableBody {

    private String name;
    private final List<MutableSection> sections = new ArrayList<>();

    /** Создаёт пустое основное тело (без имени). */
    public MutableBody() {
    }

    /**
     * Создаёт пустое тело с именем.
     *
     * @param name имя тела ({@code null} — основное, {@code "notes"} — сноски)
     */
    public MutableBody(String name) {
        this.name = name;
    }

    /**
     * Создаёт пустое тело сносок ({@code name="notes"}).
     *
     * @return новое тело сносок
     */
    public static MutableBody notes() {
        return new MutableBody("notes");
    }

    /**
     * Строит изменяемое тело из DTO (рекурсивно зеркалит секции).
     *
     * @param dto исходное тело; не может быть {@code null}
     * @return новое изменяемое тело
     */
    public static MutableBody from(BodyDto dto) {
        Objects.requireNonNull(dto, "body dto must not be null");
        MutableBody body = new MutableBody(dto.name());
        if (dto.sections() != null) {
            for (Section section : dto.sections()) {
                body.sections.add(MutableSection.from(section));
            }
        }
        return body;
    }

    /**
     * Собирает иммутабельный {@link BodyDto}.
     *
     * @return новый DTO тела
     */
    public BodyDto toDto() {
        List<Section> sectionDtos = new ArrayList<>(sections.size());
        for (MutableSection section : sections) {
            sectionDtos.add(section.toDto());
        }
        return new BodyDto(name, sectionDtos);
    }

    /**
     * Имя тела.
     *
     * @return имя ({@code null} — основное тело)
     */
    public String name() {
        return name;
    }

    /**
     * Задаёт имя тела.
     *
     * @param name новое имя ({@code null} — основное, {@code "notes"} — сноски)
     * @return этот же объект (fluent API)
     */
    public MutableBody name(String name) {
        this.name = name;
        return this;
    }

    /**
     * «Живой» список секций верхнего уровня; правки видны в {@link #toDto()}.
     *
     * @return изменяемый список секций
     */
    public List<MutableSection> sections() {
        return sections;
    }

    /**
     * Секция по индексу.
     *
     * @param index индекс
     * @return секция
     * @throws IndexOutOfBoundsException если индекс вне диапазона
     */
    public MutableSection section(int index) {
        return sections.get(index);
    }

    /**
     * Добавляет секцию в конец тела.
     *
     * @param section секция; не может быть {@code null}
     * @return этот же объект (fluent API)
     */
    public MutableBody addSection(MutableSection section) {
        sections.add(Objects.requireNonNull(section, "section must not be null"));
        return this;
    }
}
