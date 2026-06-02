package org.tehlab.whitek0t.fictionbook.api;

import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.internal.anchor.AnchorIndex;

import java.nio.file.Path;

/**
 * Потоковое чтение книг без загрузки всего контента в память.
 * Идеально для читалок: можно читать поглавно и рендерить на лету.
 * <p>
 * Пример:
 * <pre>
 * try (var streamer = FictionBookStreamer.open(path)) {
 *     var desc = streamer.readDescription();
 *     while ((var section = streamer.readNextSection()) != null) {
 *         player.play(section);
 *     }
 * }
 * </pre>
 */
public interface FictionBookStreamer extends AutoCloseable {

    static FictionBookStreamer open(Path file) throws FictionBookException {
        FictionBookFormat format = FictionBookFormat.detect(file);
        return switch (format) {
            case FB2 -> new FictionBookStreamer() {
                @Override
                public Description readDescription() throws FictionBookException {
                    return null;
                }

                @Override
                public Section readNextSection() throws FictionBookException {
                    return null;
                }

                @Override
                public Resource getResource(String id) throws FictionBookException {
                    return null;
                }

                @Override
                public AnchorIndex buildAnchorIndex() throws FictionBookException {
                    return null;
                }

                @Override
                public void close() throws Exception {

                }
            };
            case FB3 -> throw new UnsupportedOperationException("FB3 writer not implemented yet");
        };
    }

    /**
     * Читает метаданные (Jackson-парсинг, быстро).
     */
    Description readDescription() throws FictionBookException;

    /**
     * Читает следующую секцию. Возвращает null в конце книги.
     * Не держит предыдущие секции в памяти.
     */
    Section readNextSection() throws FictionBookException;

    /**
     * Лениво получает ресурс (картинку) по ID.
     */
    Resource getResource(String id) throws FictionBookException;

    /**
     * Строит индекс якорей для быстрого перехода по ссылкам.
     */
    AnchorIndex buildAnchorIndex() throws FictionBookException;
}