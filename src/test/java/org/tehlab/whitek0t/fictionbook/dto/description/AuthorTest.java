package org.tehlab.whitek0t.fictionbook.dto.description;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Author Tests")
class AuthorTests {

    @Test
    @DisplayName("should format full name with all parts")
    void shouldFormatFullNameWithAllParts() {
        Author author = new Author("Лев", "Николаевич", "Толстой");
        assertThat(author.getFullName()).isEqualTo("Лев Николаевич Толстой");
    }

    @Test
    @DisplayName("should handle missing middle name")
    void shouldHandleMissingMiddleName() {
        Author author = new Author("OCR", null, "Author");
        assertThat(author.getFullName()).isEqualTo("OCR Author");
    }

    @Test
    @DisplayName("should handle only last name")
    void shouldHandleOnlyLastName() {
        Author author = new Author(null, null, "Пушкин");
        assertThat(author.getFullName()).isEqualTo("Пушкин");
    }

    @Test
    @DisplayName("should handle blank middle name")
    void shouldHandleBlankMiddleName() {
        Author author = new Author("Иван", "   ", "Иванов");
        assertThat(author.getFullName()).isEqualTo("Иван Иванов");
    }

    @Test
    @DisplayName("should return empty string when all parts null")
    void shouldReturnEmptyWhenAllNull() {
        Author author = new Author(null, null, null);
        assertThat(author.getFullName()).isEmpty();
    }
}