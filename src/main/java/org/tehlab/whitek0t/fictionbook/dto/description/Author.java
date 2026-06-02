package org.tehlab.whitek0t.fictionbook.dto.description;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public record Author(String firstName, String middleName, String lastName) {
    /**
     * Возвращает полное имя автора в формате "Имя Отчество Фамилия".
     * Пустые и null значения корректно обрабатываются.
     *
     * Примеры:
     * - ("Лев", "Николаевич", "Толстой") → "Лев Николаевич Толстой"
     * - ("OCR", null, "Author")          → "OCR Author"
     * - (null, null, "Пушкин")           → "Пушкин"
     */
    public String getFullName() {
        return Stream.of(firstName, middleName, lastName)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
    }
}