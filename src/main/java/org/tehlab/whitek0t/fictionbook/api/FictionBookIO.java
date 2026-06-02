package org.tehlab.whitek0t.fictionbook.api;

import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.internal.reader.fb2.Fb2Reader;
import org.tehlab.whitek0t.fictionbook.internal.writer.fb2.Fb2Writer;

import java.nio.file.Path;

public final class FictionBookIO {

    private FictionBookIO() {
    }

    /**
     * Автоматически определяет формат (FB2/FB3) по magic bytes
     * и расширению, читает книгу в immutable DTO.
     */
    public static FictionBookDto read(Path file) throws FictionBookException {
        FictionBookFormat format = FictionBookFormat.detect(file);
        return read(file, format);
    }

    public static FictionBookDto read(Path file, FictionBookFormat format)
            throws FictionBookException {
        return switch (format) {
            case FB2 -> new Fb2Reader().read(file);
            case FB3 -> throw new UnsupportedOperationException("FB3 writer not implemented yet");
        };
    }

    /**
     * Записывает книгу в файл.
     * Формат определяется по расширению.
     */
    public static void write(FictionBookDto book, Path destination) throws FictionBookException {
        FictionBookFormat format = FictionBookFormat.fromPath(destination);
        write(book, destination, format);
    }

    /**
     * Записывает книгу в файл с явным указанием формата.
     */
    public static void write(FictionBookDto book, Path destination, FictionBookFormat format)
            throws FictionBookException {
        switch (format) {
            case FB2 -> new Fb2Writer().write(book, destination);
            case FB3 -> throw new UnsupportedOperationException("FB3 writer not implemented yet");
        }
    }
}