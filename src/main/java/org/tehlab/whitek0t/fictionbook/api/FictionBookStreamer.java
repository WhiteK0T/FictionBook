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

    /**
     * Открывает книгу для потокового чтения; формат определяется автоматически.
     *
     * <p><b>Внимание:</b> реализация пока заглушка — методы стримера возвращают
     * {@code null} (см. STATUS.md, раздел про Streaming API).</p>
     *
     * @param file путь к файлу книги
     * @return стример для последовательного чтения
     * @throws FictionBookException при ошибке определения формата или открытия файла
     */
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
     * Читает метаданные книги (быстрый Jackson-парсинг).
     *
     * @return метаданные книги
     * @throws FictionBookException при ошибке чтения или разбора
     */
    Description readDescription() throws FictionBookException;

    /**
     * Читает следующую секцию, не удерживая предыдущие в памяти.
     *
     * @return следующая секция или {@code null} в конце книги
     * @throws FictionBookException при ошибке чтения или разбора
     */
    Section readNextSection() throws FictionBookException;

    /**
     * Лениво получает бинарный ресурс (картинку) по его id.
     *
     * @param id идентификатор ресурса
     * @return ресурс или {@code null}, если он не найден
     * @throws FictionBookException при ошибке доступа к ресурсу
     */
    Resource getResource(String id) throws FictionBookException;

    /**
     * Строит индекс якорей для быстрого перехода по ссылкам.
     *
     * @return индекс якорей книги
     * @throws FictionBookException при ошибке построения индекса
     */
    AnchorIndex buildAnchorIndex() throws FictionBookException;
}