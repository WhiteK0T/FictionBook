package org.tehlab.whitek0t.fictionbook.internal.sanitizer;

import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.block.*;
import org.tehlab.whitek0t.fictionbook.dto.inline.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Утилита для рекурсивного обхода и трансформации immutable DTO.
 *
 * <p>Поскольку DTO построены на Java Records (immutable), любое изменение
 * требует создания новой копии. Этот класс инкапсулирует всю механику
 * рекурсивного обхода и пересоздания узлов.</p>
 *
 * <p>Пример:</p>
 * <pre>{@code
 * FictionBookDto transformed = FictionBookDtoTransformer.transform(book)
 *     .onParagraph(p -> /* ... * /)
 *     .onInlineElement(e -> /* ... * /)
 *     .apply();
 * }</pre>
 */
public class FictionBookDtoTransformer {

    private final FictionBookDto original;
    private UnaryOperator<Paragraph> paragraphTransformer = UnaryOperator.identity();
    private UnaryOperator<Section> sectionTransformer = UnaryOperator.identity();
    private Function<InlineElement, InlineElement> inlineTransformer = Function.identity();
    private Function<BlockElement, BlockElement> blockTransformer = Function.identity();

    private FictionBookDtoTransformer(FictionBookDto original) {
        this.original = original;
    }

    public static FictionBookDtoTransformer transform(FictionBookDto book) {
        return new FictionBookDtoTransformer(book);
    }

    public FictionBookDtoTransformer onParagraph(UnaryOperator<Paragraph> transformer) {
        this.paragraphTransformer = transformer;
        return this;
    }

    public FictionBookDtoTransformer onSection(UnaryOperator<Section> transformer) {
        this.sectionTransformer = transformer;
        return this;
    }

    public FictionBookDtoTransformer onInlineElement(Function<InlineElement, InlineElement> transformer) {
        this.inlineTransformer = transformer;
        return this;
    }

    public FictionBookDtoTransformer onBlockElement(Function<BlockElement, BlockElement> transformer) {
        this.blockTransformer = transformer;
        return this;
    }

    public FictionBookDto apply() {
        if (original == null) return null;

        List<BodyDto> newBodies = new ArrayList<>();
        for (BodyDto body : original.bodies()) {
            newBodies.add(transformBody(body));
        }

        return new FictionBookDto(
                original.description(),
                List.copyOf(newBodies),
                original.resources()
        );
    }

    private BodyDto transformBody(BodyDto body) {
        List<Section> newSections = new ArrayList<>();
        for (Section section : body.sections()) {
            Section transformed = transformSection(section);
            if (transformed != null) {
                newSections.add(transformed);
            }
        }
        return new BodyDto(body.name(), List.copyOf(newSections));
    }

    private Section transformSection(Section section) {
        // Трансформируем title
        List<BlockElement> newTitle = new ArrayList<>();
        if (section.title() != null) {
            for (BlockElement block : section.title()) {
                BlockElement transformed = transformBlock(block);
                if (transformed != null) newTitle.add(transformed);
            }
        }

        // Трансформируем content
        List<BlockElement> newContent = new ArrayList<>();
        if (section.content() != null) {
            for (BlockElement block : section.content()) {
                BlockElement transformed = transformBlock(block);
                if (transformed != null) newContent.add(transformed);
            }
        }

        // Рекурсивно трансформируем вложенные секции
        List<Section> newSubSections = new ArrayList<>();
        if (section.subSections() != null) {
            for (Section sub : section.subSections()) {
                Section transformed = transformSection(sub);
                if (transformed != null) newSubSections.add(transformed);
            }
        }

        Section result = new Section(
                section.id(),
                List.copyOf(newTitle),
                List.copyOf(newContent),
                List.copyOf(newSubSections),
                section.metadata()
        );

        return sectionTransformer.apply(result);
    }

    private BlockElement transformBlock(BlockElement block) {
        if (block == null) return null;

        // Сначала применяем общий блок-трансформер
        BlockElement current = blockTransformer.apply(block);
        if (current == null) return null;

        // Затем специфичные трансформации по типам
        if (current instanceof Paragraph p) {
            Paragraph transformed = transformParagraph(p);
            return paragraphTransformer.apply(transformed);
        } else if (current instanceof Poem poem) {
            return transformPoem(poem);
        } else if (current instanceof Table table) {
            return transformTable(table);
        } else if (current instanceof Cite cite) {
            return transformCite(cite);
        } else if (current instanceof Epigraph epigraph) {
            return transformEpigraph(epigraph);
        }

        return current;
    }

    private Paragraph transformParagraph(Paragraph p) {
        List<InlineElement> newElements = new ArrayList<>();
        for (InlineElement inline : p.elements()) {
            InlineElement transformed = transformInline(inline);
            if (transformed != null) newElements.add(transformed);
        }
        return new Paragraph(List.copyOf(newElements));
    }

    private InlineElement transformInline(InlineElement inline) {
        if (inline == null) return null;

        // Контейнеры с вложенными элементами — рекурсивно
        if (inline instanceof Strong s) {
            return new Strong(transformInlineList(s.elements()));
        } else if (inline instanceof Emphasis e) {
            return new Emphasis(transformInlineList(e.elements()));
        } else if (inline instanceof Strikethrough s) {
            return new Strikethrough(transformInlineList(s.elements()));
        } else if (inline instanceof Sub s) {
            return new Sub(transformInlineList(s.elements()));
        } else if (inline instanceof Sup s) {
            return new Sup(transformInlineList(s.elements()));
        } else if (inline instanceof Link l) {
            return new Link(l.href(), l.type(), transformInlineList(l.elements()));
        }

        // Листовые элементы — применяем трансформер
        return inlineTransformer.apply(inline);
    }

    private List<InlineElement> transformInlineList(List<InlineElement> elements) {
        List<InlineElement> result = new ArrayList<>();
        for (InlineElement e : elements) {
            InlineElement transformed = transformInline(e);
            if (transformed != null) result.add(transformed);
        }
        return List.copyOf(result);
    }

    private Poem transformPoem(Poem poem) {
        List<BlockElement> newTitle = transformBlockList(poem.title());
        List<Stanza> newStanzas = new ArrayList<>();
        for (Stanza stanza : poem.stanzas()) {
            List<Verse> newVerses = new ArrayList<>();
            for (Verse verse : stanza.verses()) {
                newVerses.add(new Verse(transformInlineList(verse.elements())));
            }
            newStanzas.add(new Stanza(List.copyOf(newVerses)));
        }
        List<BlockElement> newEpigraph = transformBlockList(poem.epigraph());
        return new Poem(poem.id(), newTitle, List.copyOf(newStanzas), newEpigraph, poem.author());
    }

    private Table transformTable(Table table) {
        List<TableRow> newRows = new ArrayList<>();
        for (TableRow row : table.rows()) {
            List<TableCell> newCells = new ArrayList<>();
            for (TableCell cell : row.cells()) {
                newCells.add(new TableCell(transformBlockList(cell.content())));
            }
            newRows.add(new TableRow(List.copyOf(newCells)));
        }
        return new Table(List.copyOf(newRows));
    }

    private Cite transformCite(Cite cite) {
        return new Cite(cite.id(), transformBlockList(cite.content()), cite.author());
    }

    private Epigraph transformEpigraph(Epigraph epigraph) {
        return new Epigraph(epigraph.id(), transformBlockList(epigraph.content()), epigraph.author());
    }

    private List<BlockElement> transformBlockList(List<BlockElement> blocks) {
        if (blocks == null) return List.of();
        List<BlockElement> result = new ArrayList<>();
        for (BlockElement block : blocks) {
            BlockElement transformed = transformBlock(block);
            if (transformed != null) result.add(transformed);
        }
        return List.copyOf(result);
    }
}
