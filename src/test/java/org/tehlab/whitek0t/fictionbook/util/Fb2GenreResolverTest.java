package org.tehlab.whitek0t.fictionbook.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fb2GenreResolver Tests")
class Fb2GenreResolverTest {

    @Test
    @DisplayName("известные коды переводятся")
    void knownCodes() {
        assertThat(Fb2GenreResolver.humanize("prose_classic")).isEqualTo("Классическая проза");
        assertThat(Fb2GenreResolver.humanize("sf_fantasy")).isEqualTo("Фэнтези");
        assertThat(Fb2GenreResolver.humanize("detective")).isEqualTo("Детектив");
    }

    @Test
    @DisplayName("регистр и пробелы игнорируются")
    void caseInsensitive() {
        assertThat(Fb2GenreResolver.humanize("  PROSE_Classic ")).isEqualTo("Классическая проза");
    }

    @Test
    @DisplayName("неизвестный код возвращается как есть")
    void unknownPassthrough() {
        assertThat(Fb2GenreResolver.humanize("totally_unknown_genre"))
                .isEqualTo("totally_unknown_genre");
    }

    @Test
    @DisplayName("null/пустой код возвращается без изменений")
    void nullSafe() {
        assertThat(Fb2GenreResolver.humanize(null)).isNull();
        assertThat(Fb2GenreResolver.humanize("")).isEmpty();
    }
}
