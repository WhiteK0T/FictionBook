package org.tehlab.whitek0t.fictionbook.exception;

/**
 * Базовое проверяемое исключение библиотеки. Любая ошибка чтения/записи/валидации
 * FB2/FB3 всплывает наружу как {@code FictionBookException} (или его подкласс
 * {@link InvalidFormatException}).
 */
public class FictionBookException extends Exception {

    /**
     * Создаёт исключение с сообщением.
     *
     * @param message человекочитаемое описание ошибки
     */
    public FictionBookException(String message) {
        super(message);
    }

    /**
     * Создаёт исключение с сообщением и первопричиной.
     *
     * @param message человекочитаемое описание ошибки
     * @param cause   исходное исключение-первопричина
     */
    public FictionBookException(String message, Throwable cause) {
        super(message, cause);
    }
}
