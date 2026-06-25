package org.tehlab.whitek0t.fictionbook.api;

import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;
import org.tehlab.whitek0t.fictionbook.dto.description.Author;
import org.tehlab.whitek0t.fictionbook.dto.description.Sequence;
import org.tehlab.whitek0t.fictionbook.internal.info.BookInfoExtractor;
import org.tehlab.whitek0t.fictionbook.util.Fb2GenreResolver;

import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Краткая сводка о книге для отображения: метаданные из {@code description} плюс
 * посчитанный объём текста и обложка.
 *
 * <p>Собирается из {@link FictionBookDto} фабриками {@link #from(FictionBookDto)} /
 * {@link #from(FictionBookDto, String)} либо через {@link FictionBookIO#info}. Поля,
 * которых нет в книге, равны {@code null} (списки — пустые). Понятие «страниц» в
 * FB2/FB3 отсутствует (страница — артефакт рендера), поэтому объём выражен в
 * фактических {@link #charCount} символах и {@link #wordCount} словах текста тела.</p>
 *
 * @param fileName       имя исходного файла или {@code null}, если книга пришла из DTO
 * @param title          название книги
 * @param authors        авторы (пустой список, если не указаны)
 * @param genres         коды жанров FB2 (см. {@link #genreNames()} для названий)
 * @param lang           язык книги
 * @param sequence       цикл/серия (название и номер) или {@code null}
 * @param year           год издания (строкой, как в книге)
 * @param publisher      издательство
 * @param city           город издания
 * @param isbn           ISBN
 * @param annotationText аннотация в плоском тексте (абзацы через {@code \n})
 * @param annotation     аннотация структурно, как в DTO
 * @param charCount      число символов в тексте тела (пробелы схлопнуты)
 * @param wordCount      число слов в тексте тела
 * @param cover          ресурс обложки или {@code null}, если её нет
 * @param links          href внешних ссылок ({@code <a>}) книги — из аннотации и
 *                       тела, без повторов, в порядке появления; внутренние якоря
 *                       (начинающиеся с {@code #}) не включаются
 */
public record BookInfo(
        String fileName,
        String title,
        List<Author> authors,
        List<String> genres,
        String lang,
        Sequence sequence,
        String year,
        String publisher,
        String city,
        String isbn,
        String annotationText,
        List<BlockElement> annotation,
        int charCount,
        int wordCount,
        Resource cover,
        List<String> links
) {

    /**
     * Собирает сводку из уже разобранной книги (без имени файла).
     *
     * @param dto книга
     * @return сводка с {@code fileName == null}
     */
    public static BookInfo from(FictionBookDto dto) {
        return BookInfoExtractor.extract(dto, null);
    }

    /**
     * Собирает сводку из книги с указанием имени исходного файла.
     *
     * @param dto      книга
     * @param fileName имя файла или {@code null}
     * @return заполненная сводка
     */
    public static BookInfo from(FictionBookDto dto, String fileName) {
        return BookInfoExtractor.extract(dto, fileName);
    }

    /**
     * Авторы одной строкой, например {@code "Юрий Винокуров, Олег Сапфир"}.
     *
     * @return авторы через запятую (пустая строка, если их нет)
     */
    public String authorsLine() {
        return authors.stream()
                .map(Author::getFullName)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    /**
     * Человекочитаемые названия жанров (коды переведены через
     * {@link Fb2GenreResolver}; неизвестные коды остаются как есть).
     *
     * @return список названий жанров
     */
    public List<String> genreNames() {
        return genres.stream().map(Fb2GenreResolver::humanize).toList();
    }

    /**
     * Есть ли у книги обложка.
     *
     * @return {@code true}, если {@link #cover} не {@code null}
     */
    public boolean hasCover() {
        return cover != null;
    }

    /**
     * Многострочное человекочитаемое представление сводки. Пустые поля опускаются.
     *
     * @return текст вида «Книга: …\nАвтор: …\n…»
     */
    public String toDisplayString() {
        StringJoiner sj = new StringJoiner("\n");
        sj.add("Книга: " + (title == null ? "—" : "\"" + title + "\""));
        if (fileName != null) sj.add("Имя файла: \"" + fileName + "\"");

        String a = authorsLine();
        if (!a.isBlank()) sj.add("Автор: " + a);

        List<String> gn = genreNames();
        if (!gn.isEmpty()) sj.add("Жанр: " + String.join(", ", gn));

        if (sequence != null && sequence.name() != null && !sequence.name().isBlank()) {
            String s = sequence.name();
            if (sequence.number() != null) s += " #" + sequence.number();
            sj.add("Цикл: " + s);
        }

        if (notBlank(year)) sj.add("Год выхода: " + year);
        if (notBlank(publisher)) sj.add("Издательство: " + publisher);
        if (notBlank(city)) sj.add("Город: " + city);
        if (notBlank(isbn)) sj.add("ISBN: " + isbn);
        if (notBlank(lang)) sj.add("Язык: " + lang);

        sj.add("Объём: " + charCount + " симв., " + wordCount + " слов");
        sj.add("Обложка: " + (hasCover() ? "есть" : "нет"));

        if (notBlank(annotationText)) sj.add("Описание: " + annotationText);

        if (links != null && !links.isEmpty()) {
            StringJoiner linkLines = new StringJoiner("\n");
            linkLines.add("Ссылки:");
            links.forEach(href -> linkLines.add("- " + href));
            sj.add(linkLines.toString());
        }

        return sj.toString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
