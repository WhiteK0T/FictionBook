package org.tehlab.whitek0t.fictionbook.internal.parser.stax;

import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.block.TableCell;
import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Строит {@link TableCell} из блочных элементов.
 *
 * <p>Согласно спецификации FB2, внутри {@code <td>} должны быть только
 * блочные элементы (обычно {@code <p>}). Но в реальных файлах часто
 * встречается "грязный" FB2:</p>
 * <pre>{@code
 * <td>Привет</td>  <!-- неправильно, должно быть <td><p>Привет</p></td> -->
 * }</pre>
 *
 * <p>Этот билдер автоматически оборачивает "осиротевший" текст и инлайн-элементы
 * в неявный {@link Paragraph} (прощающий режим).</p>
 */
public class TableCellBuilder implements NodeBuilder {

    private final List<BlockElement> blocks = new ArrayList<>();
    private ParagraphBuilder implicitParagraph = null;

    @Override
    public void appendText(String text) {
        if (text == null) return;

        // Если пришёл текст, значит мы в "грязном" режиме — создаём неявный параграф
        if (implicitParagraph == null) {
            implicitParagraph = new ParagraphBuilder();
        }
        implicitParagraph.appendText(text);
    }

    @Override
    public void addChild(Object childNode) {
        if (childNode instanceof BlockElement block) {
            // Пришёл блочный элемент — сбрасываем неявный параграф (если был)
            flushImplicitParagraph();
            blocks.add(block);
        } else if (childNode instanceof InlineElement inline) {
            // Пришёл инлайн-элемент — добавляем в неявный параграф
            if (implicitParagraph == null) {
                implicitParagraph = new ParagraphBuilder();
            }
            implicitParagraph.addChild(inline);
        }
        // Игнорируем остальные типы
    }

    @Override
    public Object build() {
        flushImplicitParagraph();

        // Фильтруем пустые параграфы (только whitespace)
        List<BlockElement> filteredBlocks = new ArrayList<>();
        for (BlockElement block : blocks) {
            if (block instanceof Paragraph p) {
                // Проверяем, есть ли в параграфе непустой текст
                boolean hasContent = p.elements().stream()
                        .anyMatch(e -> {
                            if (e instanceof Text t) {
                                return t.value() != null && !t.value().isBlank();
                            }
                            return true; // Не-текстовые элементы считаем контентом
                        });
                if (hasContent) {
                    filteredBlocks.add(block);
                }
            } else {
                filteredBlocks.add(block);
            }
        }

        return new TableCell(List.copyOf(filteredBlocks));
    }

    /**
     * Сбрасывает накопленный неявный параграф в список блоков.
     */
    private void flushImplicitParagraph() {
        if (implicitParagraph != null) {
            Paragraph para = (Paragraph) implicitParagraph.build();
            // Не добавляем пустые параграфы
            if (!para.elements().isEmpty()) {
                blocks.add(para);
            }
            implicitParagraph = null;
        }
    }
}
