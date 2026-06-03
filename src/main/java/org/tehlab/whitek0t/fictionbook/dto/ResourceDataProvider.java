package org.tehlab.whitek0t.fictionbook.dto;

import java.io.IOException;
import java.io.InputStream;

/**
 * Поставщик байтов бинарного ресурса. Функциональный интерфейс: позволяет получать
 * данные {@link Resource} лениво и многократно (каждый вызов отдаёт свежий поток),
 * не загружая все бинарники в память сразу.
 *
 * @see Resource
 */
@FunctionalInterface
public interface ResourceDataProvider {
    /**
     * Открывает новый поток с байтами ресурса. Вызывающий обязан закрыть поток.
     *
     * @return свежий поток данных ресурса
     * @throws IOException если данные недоступны или произошла ошибка ввода-вывода
     */
    InputStream getInputStream() throws IOException;
}
