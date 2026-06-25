package org.tehlab.whitek0t.fictionbook.util;

import java.util.Map;

/**
 * Переводит коды жанров FictionBook (например {@code "prose_classic"}) в
 * человекочитаемые названия на русском (например {@code "Классическая проза"}).
 * <p>
 * Справочник основан на стандартном списке жанров FB2 (genres из FictionBook
 * Editor). Неизвестный код возвращается как есть — вызывающий всегда получает
 * непустую строку.
 */
public final class Fb2GenreResolver {

    private Fb2GenreResolver() {
    }

    private static final Map<String, String> NAMES = Map.<String, String>ofEntries(
            // --- Научная фантастика ---
            Map.entry("sf_history", "Альтернативная история"),
            Map.entry("sf_action", "Боевая фантастика"),
            Map.entry("sf_epic", "Эпическая фантастика"),
            Map.entry("sf_heroic", "Героическая фантастика"),
            Map.entry("sf_detective", "Детективная фантастика"),
            Map.entry("sf_cyberpunk", "Киберпанк"),
            Map.entry("sf_space", "Космическая фантастика"),
            Map.entry("sf_social", "Социально-психологическая фантастика"),
            Map.entry("sf_horror", "Ужасы и мистика"),
            Map.entry("sf_humor", "Юмористическая фантастика"),
            Map.entry("sf_fantasy", "Фэнтези"),
            Map.entry("sf_fantasy_city", "Городское фэнтези"),
            Map.entry("sf_litrpg", "ЛитРПГ"),
            Map.entry("sf_postapocalyptic", "Постапокалипсис"),
            Map.entry("sf_stimpank", "Стимпанк"),
            Map.entry("sf", "Научная фантастика"),
            Map.entry("popadanec", "Попаданцы"),

            // --- Детективы и триллеры ---
            Map.entry("det_classic", "Классический детектив"),
            Map.entry("det_police", "Полицейский детектив"),
            Map.entry("det_action", "Боевик"),
            Map.entry("det_irony", "Иронический детектив"),
            Map.entry("det_history", "Исторический детектив"),
            Map.entry("det_espionage", "Шпионский детектив"),
            Map.entry("det_crime", "Криминальный детектив"),
            Map.entry("det_political", "Политический детектив"),
            Map.entry("det_maniac", "Маньяки"),
            Map.entry("det_hard", "Крутой детектив"),
            Map.entry("thriller", "Триллер"),
            Map.entry("detective", "Детектив"),

            // --- Проза ---
            Map.entry("prose_classic", "Классическая проза"),
            Map.entry("prose_history", "Историческая проза"),
            Map.entry("prose_contemporary", "Современная проза"),
            Map.entry("prose_counter", "Контркультура"),
            Map.entry("prose_rus_classic", "Русская классическая проза"),
            Map.entry("prose_su_classics", "Советская классическая проза"),
            Map.entry("prose_military", "Военная проза"),

            // --- Любовные романы ---
            Map.entry("love_contemporary", "Современные любовные романы"),
            Map.entry("love_history", "Исторические любовные романы"),
            Map.entry("love_detective", "Остросюжетные любовные романы"),
            Map.entry("love_short", "Короткие любовные романы"),
            Map.entry("love_erotica", "Эротика"),
            Map.entry("love_sf", "Любовное фэнтези"),
            Map.entry("love", "Любовные романы"),

            // --- Приключения ---
            Map.entry("adv_western", "Вестерн"),
            Map.entry("adv_history", "Исторические приключения"),
            Map.entry("adv_indian", "Приключения про индейцев"),
            Map.entry("adv_maritime", "Морские приключения"),
            Map.entry("adv_geo", "Путешествия и география"),
            Map.entry("adv_animal", "Природа и животные"),
            Map.entry("adventure", "Приключения"),

            // --- Детское ---
            Map.entry("child_tale", "Сказка"),
            Map.entry("child_verse", "Детские стихи"),
            Map.entry("child_prose", "Детская проза"),
            Map.entry("child_sf", "Детская фантастика"),
            Map.entry("child_det", "Детские остросюжетные"),
            Map.entry("child_adv", "Детские приключения"),
            Map.entry("child_education", "Детская образовательная литература"),
            Map.entry("children", "Детская литература"),

            // --- Поэзия и драматургия ---
            Map.entry("poetry", "Поэзия"),
            Map.entry("dramaturgy", "Драматургия"),

            // --- Старинное ---
            Map.entry("antique_ant", "Античная литература"),
            Map.entry("antique_european", "Европейская старинная литература"),
            Map.entry("antique_russian", "Древнерусская литература"),
            Map.entry("antique_east", "Древневосточная литература"),
            Map.entry("antique_myths", "Мифы, легенды, эпос"),
            Map.entry("antique", "Старинная литература"),

            // --- Наука, образование ---
            Map.entry("sci_history", "История"),
            Map.entry("sci_psychology", "Психология"),
            Map.entry("sci_culture", "Культурология"),
            Map.entry("sci_religion", "Религиоведение"),
            Map.entry("sci_philosophy", "Философия"),
            Map.entry("sci_politics", "Политика"),
            Map.entry("sci_business", "Деловая литература"),
            Map.entry("sci_juris", "Юриспруденция"),
            Map.entry("sci_linguistic", "Языкознание"),
            Map.entry("sci_medicine", "Медицина"),
            Map.entry("sci_phys", "Физика"),
            Map.entry("sci_math", "Математика"),
            Map.entry("sci_chem", "Химия"),
            Map.entry("sci_biology", "Биология"),
            Map.entry("sci_tech", "Технические науки"),
            Map.entry("science", "Научная литература"),

            // --- Компьютеры ---
            Map.entry("comp_www", "Интернет"),
            Map.entry("comp_programming", "Программирование"),
            Map.entry("comp_hard", "Компьютерное железо"),
            Map.entry("comp_soft", "Программы"),
            Map.entry("comp_db", "Базы данных"),
            Map.entry("comp_osnet", "ОС и сети"),
            Map.entry("computers", "Компьютерная литература"),

            // --- Справочники ---
            Map.entry("ref_encyc", "Энциклопедии"),
            Map.entry("ref_dict", "Словари"),
            Map.entry("ref_ref", "Справочники"),
            Map.entry("ref_guide", "Руководства"),
            Map.entry("reference", "Справочная литература"),

            // --- Документальное ---
            Map.entry("nonf_biography", "Биографии и мемуары"),
            Map.entry("nonf_publicism", "Публицистика"),
            Map.entry("nonf_criticism", "Критика"),
            Map.entry("nonfiction", "Документальная литература"),
            Map.entry("design", "Искусство и дизайн"),

            // --- Религия ---
            Map.entry("religion_rel", "Религия"),
            Map.entry("religion_esoterics", "Эзотерика"),
            Map.entry("religion_self", "Самосовершенствование"),
            Map.entry("religion", "Религиозная литература"),

            // --- Юмор ---
            Map.entry("humor_anecdote", "Анекдоты"),
            Map.entry("humor_prose", "Юмористическая проза"),
            Map.entry("humor_verse", "Юмористические стихи"),
            Map.entry("humor", "Юмор"),

            // --- Дом и семья ---
            Map.entry("home_cooking", "Кулинария"),
            Map.entry("home_pets", "Домашние животные"),
            Map.entry("home_crafts", "Хобби и ремёсла"),
            Map.entry("home_entertain", "Развлечения"),
            Map.entry("home_health", "Здоровье"),
            Map.entry("home_garden", "Сад и огород"),
            Map.entry("home_diy", "Сделай сам"),
            Map.entry("home_sport", "Спорт"),
            Map.entry("home_sex", "Семейные отношения"),
            Map.entry("home", "Домоводство")
    );

    /**
     * Возвращает человекочитаемое название жанра по его коду.
     *
     * @param code код жанра FB2 (например {@code "prose_classic"})
     * @return русское название либо исходный код, если он неизвестен;
     *         {@code null}/пустой код возвращается без изменений
     */
    public static String humanize(String code) {
        if (code == null || code.isBlank()) {
            return code;
        }
        return NAMES.getOrDefault(code.trim().toLowerCase(), code);
    }
}
