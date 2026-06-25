package org.tehlab.whitek0t.fictionbook.api;

import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.internal.reader.fb2.Fb2Reader;
import org.tehlab.whitek0t.fictionbook.internal.reader.fb3.Fb3Reader;
import org.tehlab.whitek0t.fictionbook.internal.writer.fb2.Fb2Writer;
import org.tehlab.whitek0t.fictionbook.internal.writer.fb3.Fb3Writer;

import java.nio.file.Path;

/**
 * Единая точка входа библиотеки: чтение и запись книг. Формат (FB2/FB3) определяется
 * автоматически — при чтении по magic bytes/расширению, при записи по расширению файла.
 *
 * <p>Утилитный класс со статическими методами; не инстанцируется. Поддержаны чтение и
 * запись обоих форматов — FB2 ({@code Fb2Reader}/{@code Fb2Writer}) и FB3
 * ({@code Fb3Reader}/{@code Fb3Writer}).</p>
 */
public final class FictionBookIO {

    private FictionBookIO() {
    }

    /**
     * Автоматически определяет формат (FB2/FB3) по magic bytes и расширению и читает
     * книгу в неизменяемый DTO.
     *
     * @param file путь к файлу книги
     * @return разобранная книга
     * @throws FictionBookException при ошибке определения формата, чтения или разбора
     */
    public static FictionBookDto read(Path file) throws FictionBookException {
        FictionBookFormat format = FictionBookFormat.detect(file);
        return read(file, format);
    }

    /**
     * Читает книгу в неизменяемый DTO с явно заданным форматом.
     *
     * @param file   путь к файлу книги
     * @param format формат файла
     * @return разобранная книга
     * @throws FictionBookException при ошибке чтения или разбора
     */
    public static FictionBookDto read(Path file, FictionBookFormat format)
            throws FictionBookException {
        return switch (format) {
            case FB2 -> new Fb2Reader().read(file);
            case FB3 -> new Fb3Reader().read(file);
        };
    }

    /**
     * Читает книгу и возвращает краткую сводку {@link BookInfo} (с именем файла).
     *
     * @param file путь к файлу книги
     * @return сводка о книге
     * @throws FictionBookException при ошибке определения формата, чтения или разбора
     */
    public static BookInfo info(Path file) throws FictionBookException {
        FictionBookDto book = read(file);
        return BookInfo.from(book, file.getFileName().toString());
    }

    /**
     * Возвращает краткую сводку {@link BookInfo} по уже разобранной книге
     * (без имени файла).
     *
     * @param book разобранная книга
     * @return сводка о книге
     */
    public static BookInfo info(FictionBookDto book) {
        return BookInfo.from(book);
    }

    /**
     * Записывает книгу в файл; формат определяется по расширению назначения.
     *
     * @param book        книга для записи
     * @param destination путь назначения (расширение задаёт формат)
     * @throws FictionBookException при ошибке определения формата или записи
     */
    public static void write(FictionBookDto book, Path destination) throws FictionBookException {
        FictionBookFormat format = FictionBookFormat.fromPath(destination);
        write(book, destination, format);
    }

    /**
     * Записывает книгу в файл с явно заданным форматом.
     *
     * @param book        книга для записи
     * @param destination путь назначения
     * @param format      формат файла
     * @throws FictionBookException при ошибке записи
     */
    public static void write(FictionBookDto book, Path destination, FictionBookFormat format)
            throws FictionBookException {
        switch (format) {
            case FB2 -> new Fb2Writer().write(book, destination);
            case FB3 -> new Fb3Writer().write(book, destination);
        }
    }
}