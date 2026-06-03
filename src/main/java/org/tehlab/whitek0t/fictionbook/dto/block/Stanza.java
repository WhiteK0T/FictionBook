package org.tehlab.whitek0t.fictionbook.dto.block;

import java.util.List;

/**
 * Строфа стихотворения — FB2-элемент {@code <stanza>}. Группа стихотворных строк
 * ({@link Verse}).
 *
 * @param verses строки строфы
 * @see Poem
 */
public record Stanza(List<Verse> verses) implements BlockElement {
}
