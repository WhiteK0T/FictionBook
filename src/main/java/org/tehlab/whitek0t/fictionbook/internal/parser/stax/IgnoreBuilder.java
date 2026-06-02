package org.tehlab.whitek0t.fictionbook.internal.parser.stax;

/**
 * "Поглотитель" для неизвестных тегов в прощающем режиме.
 *
 * <p>Когда парсер встречает тег, которого нет в спецификации FB2
 * (или который не поддерживается библиотекой), создаётся IgnoreBuilder.
 * Он принимает все события (текст, дочерние теги), но ничего не делает.
 * При {@link #build()} возвращает {@code null}, и родительский билдер
 * игнорирует этот результат.</p>
 *
 * <p>Пример: если в FB2 встретился {@code <custom-tag>текст</custom-tag>},
 * парсер не упадёт, а просто пропустит этот фрагмент.</p>
 */
public class IgnoreBuilder implements NodeBuilder {

    @Override
    public void appendText(String text) {
        // Поглощаем
    }

    @Override
    public void addChild(Object childNode) {
        // Поглощаем
    }

    @Override
    public Object build() {
        return null; // Родительский билдер проигнорирует null
    }
}