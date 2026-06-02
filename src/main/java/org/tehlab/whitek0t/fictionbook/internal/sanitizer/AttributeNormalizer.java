package org.tehlab.whitek0t.fictionbook.internal.sanitizer;

import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;

/**
 * Нормализует атрибуты узлов.
 *
 * <p>Выполняет следующие преобразования:</p>
 * <ul>
 *   <li>Пустые строки в id заменяются на null</li>
 *   <li>Пробелы в id обрезаются</li>
 *   <li>Недопустимые символы в id заменяются на подчёркивания</li>
 * </ul>
 *
 * <p>Это гарантирует, что при записи XML все атрибуты будут валидными
 * согласно спецификации.</p>
 */
public class AttributeNormalizer implements Sanitizer {

    // ID в XML должен соответствовать NCName:
    // начинается с буквы или _, содержит буквы, цифры, -, _, .
    private static final java.util.regex.Pattern VALID_ID =
            java.util.regex.Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.-]*$");

    @Override
    public FictionBookDto sanitize(FictionBookDto book) {
        return FictionBookDtoTransformer.transform(book)
                .onSection(this::normalizeSection)
                .apply();
    }

    private Section normalizeSection(Section section) {
        String normalizedId = normalizeId(section.id());

        if (java.util.Objects.equals(normalizedId, section.id())) {
            return section; // Ничего не изменилось
        }

        return new Section(
                normalizedId,
                section.title(),
                section.content(),
                section.subSections(),
                section.metadata()
        );
    }

    private String normalizeId(String id) {
        if (id == null) return null;

        String trimmed = id.trim();
        if (trimmed.isEmpty()) return null;

        // Если ID уже валидный — возвращаем как есть
        if (VALID_ID.matcher(trimmed).matches()) {
            return trimmed;
        }

        // Иначе нормализуем: заменяем недопустимые символы на _
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (i == 0) {
                if (Character.isLetter(c) || c == '_') {
                    sb.append(c);
                } else {
                    sb.append('_');
                }
            } else {
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
                    sb.append(c);
                } else {
                    sb.append('_');
                }
            }
        }

        String result = sb.toString();
        return result.isEmpty() ? null : result;
    }
}
