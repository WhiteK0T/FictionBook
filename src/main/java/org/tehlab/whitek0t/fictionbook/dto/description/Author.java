package org.tehlab.whitek0t.fictionbook.dto.description;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Автор — FB2-элемент {@code <author>}. Используется как в {@link TitleInfo}
 * (авторы книги), так и в {@link DocumentInfo} (создатели FB2-файла).
 *
 * @param firstName  имя; может быть {@code null}
 * @param middleName отчество; может быть {@code null}
 * @param lastName   фамилия; может быть {@code null}
 */
public record Author(String firstName, String middleName, String lastName) {
    /**
     * Возвращает полное имя автора в формате «Имя Отчество Фамилия».
     * Пустые и {@code null}-значения пропускаются, лишние пробелы не возникают.
     *
     * <p>Примеры:</p>
     * <ul>
     *   <li>{@code ("Лев", "Николаевич", "Толстой")} → {@code "Лев Николаевич Толстой"}</li>
     *   <li>{@code ("OCR", null, "Author")} → {@code "OCR Author"}</li>
     *   <li>{@code (null, null, "Пушкин")} → {@code "Пушкин"}</li>
     * </ul>
     *
     * @return склеенное имя; пустая строка, если все части пусты
     */
    public String getFullName() {
        return Stream.of(firstName, middleName, lastName)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
    }
}
