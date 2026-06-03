package org.tehlab.whitek0t.fictionbook.exception;

import javax.xml.stream.Location;
import java.nio.file.Path;

/**
 * Исключение, выбрасываемое при обнаружении некорректного формата файла FB2/FB3.
 * <p>
 * Используется в случаях:
 * <ul>
 *   <li>Отсутствие обязательных элементов (например, {@code <description>})</li>
 *   <li>Невалидные значения атрибутов</li>
 *   <li>Нарушение структуры XML (незакрытые теги, дубликаты)</li>
 *   <li>Неподдерживаемые версии формата</li>
 *   <li>Повреждённые ZIP-архивы (для FB3)</li>
 * </ul>
 * <p>
 * Содержит детальную информацию о месте ошибки (файл, строка, колонка, элемент),
 * что позволяет давать пользователю понятные сообщения.
 *
 * <p>Экземпляры удобнее создавать не конструктором, а статическими фабричными
 * методами ({@link #missingElement}, {@link #unexpectedElement}, …).</p>
 *
 * <p>Пример использования:</p>
 * <pre>
 * if (description == null) {
 *     throw InvalidFormatException.missingElement(fileName, "description");
 * }
 *
 * // Или с информацией о позиции из StAX:
 * throw InvalidFormatException.unexpectedElement(
 *     fileName, "body", xmlReader.getLocation()
 * );
 * </pre>
 */
public class InvalidFormatException extends FictionBookException {

    /** Имя файла с ошибкой или {@code null}, если неизвестно. */
    private final String fileName;
    /** Номер строки или {@code -1}, если неизвестно. */
    private final int line;
    /** Номер колонки или {@code -1}, если неизвестно. */
    private final int column;
    /** Имя XML-элемента или {@code null}, если неизвестно. */
    private final String elementName;

    /**
     * Базовый конструктор без сведений о месте ошибки.
     *
     * @param message человекочитаемое описание ошибки
     */
    public InvalidFormatException(String message) {
        super(message);
        this.fileName = null;
        this.line = -1;
        this.column = -1;
        this.elementName = null;
    }

    /**
     * Конструктор с первопричиной, без сведений о месте ошибки.
     *
     * @param message человекочитаемое описание ошибки
     * @param cause   исходное исключение-первопричина
     */
    public InvalidFormatException(String message, Throwable cause) {
        super(message, cause);
        this.fileName = null;
        this.line = -1;
        this.column = -1;
        this.elementName = null;
    }

    /**
     * Полный конструктор с информацией о месте ошибки.
     *
     * @param message     человекочитаемое описание ошибки
     * @param fileName    имя файла, где обнаружена ошибка (или {@code null})
     * @param line        номер строки (или {@code -1}, если неизвестно)
     * @param column      номер колонки (или {@code -1}, если неизвестно)
     * @param elementName имя XML-элемента (или {@code null})
     */
    public InvalidFormatException(String message, String fileName, int line, int column, String elementName) {
        super(message);
        this.fileName = fileName;
        this.line = line;
        this.column = column;
        this.elementName = elementName;
    }

    /**
     * Полный конструктор с первопричиной и информацией о месте ошибки.
     *
     * @param message     человекочитаемое описание ошибки
     * @param cause       исходное исключение-первопричина
     * @param fileName    имя файла, где обнаружена ошибка (или {@code null})
     * @param line        номер строки (или {@code -1}, если неизвестно)
     * @param column      номер колонки (или {@code -1}, если неизвестно)
     * @param elementName имя XML-элемента (или {@code null})
     */
    public InvalidFormatException(String message, Throwable cause,
                                  String fileName, int line, int column, String elementName) {
        super(message, cause);
        this.fileName = fileName;
        this.line = line;
        this.column = column;
        this.elementName = elementName;
    }

    // ========================================================================
    // GETTERS
    // ========================================================================

    /**
     * Имя файла, в котором обнаружена ошибка.
     *
     * @return имя файла или {@code null}, если неизвестно
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Номер строки с ошибкой.
     *
     * @return номер строки или {@code -1}, если неизвестно
     */
    public int getLine() {
        return line;
    }

    /**
     * Номер колонки с ошибкой.
     *
     * @return номер колонки или {@code -1}, если неизвестно
     */
    public int getColumn() {
        return column;
    }

    /**
     * Имя XML-элемента, в котором обнаружена ошибка.
     *
     * @return имя элемента или {@code null}, если неизвестно
     */
    public String getElementName() {
        return elementName;
    }

    /**
     * Проверяет, есть ли сведения о месте ошибки.
     *
     * @return {@code true}, если известны файл, строка или элемент
     */
    public boolean hasLocation() {
        return line > 0 || fileName != null || elementName != null;
    }

    // ========================================================================
    // ФОРМАТИРОВАНИЕ СООБЩЕНИЯ
    // ========================================================================

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getMessage());

        if (hasLocation()) {
            sb.append(" [");

            boolean first = true;
            if (fileName != null) {
                sb.append("file=").append(fileName);
                first = false;
            }

            if (line > 0) {
                if (!first) sb.append(", ");
                sb.append("line=").append(line);
                if (column > 0) {
                    sb.append(", column=").append(column);
                }
                first = false;
            }

            if (elementName != null) {
                if (!first) sb.append(", ");
                sb.append("element=<").append(elementName).append(">");
            }

            sb.append("]");
        }

        return sb.toString();
    }

    // ========================================================================
    // ФАБРИЧНЫЕ МЕТОДЫ
    // ========================================================================

    /**
     * Отсутствует обязательный элемент.
     *
     * @param fileName    имя файла
     * @param elementName имя отсутствующего элемента
     * @return готовое исключение
     */
    public static InvalidFormatException missingElement(String fileName, String elementName) {
        return new InvalidFormatException(
                "Required element is missing: <" + elementName + ">",
                fileName, -1, -1, elementName
        );
    }

    /**
     * Отсутствует обязательный элемент (с позицией).
     *
     * @param fileName    имя файла
     * @param elementName имя отсутствующего элемента
     * @param location    позиция в XML (может быть {@code null})
     * @return готовое исключение
     */
    public static InvalidFormatException missingElement(String fileName, String elementName, Location location) {
        return new InvalidFormatException(
                "Required element is missing: <" + elementName + ">",
                fileName,
                location != null ? location.getLineNumber() : -1,
                location != null ? location.getColumnNumber() : -1,
                elementName
        );
    }

    /**
     * Отсутствует обязательный атрибут.
     *
     * @param fileName      имя файла
     * @param elementName   имя элемента, в котором ожидался атрибут
     * @param attributeName имя отсутствующего атрибута
     * @param location      позиция в XML (может быть {@code null})
     * @return готовое исключение
     */
    public static InvalidFormatException missingAttribute(String fileName, String elementName,
                                                          String attributeName, Location location) {
        return new InvalidFormatException(
                "Required attribute '" + attributeName + "' is missing in element <" + elementName + ">",
                fileName,
                location != null ? location.getLineNumber() : -1,
                location != null ? location.getColumnNumber() : -1,
                elementName
        );
    }

    /**
     * Невалидное значение атрибута.
     *
     * @param fileName      имя файла
     * @param elementName   имя элемента
     * @param attributeName имя атрибута
     * @param value         фактическое (некорректное) значение
     * @param reason        причина, почему значение невалидно
     * @param location      позиция в XML (может быть {@code null})
     * @return готовое исключение
     */
    public static InvalidFormatException invalidAttributeValue(String fileName, String elementName,
                                                               String attributeName, String value,
                                                               String reason, Location location) {
        return new InvalidFormatException(
                "Invalid value '" + value + "' for attribute '" + attributeName +
                        "' in element <" + elementName + ">: " + reason,
                fileName,
                location != null ? location.getLineNumber() : -1,
                location != null ? location.getColumnNumber() : -1,
                elementName
        );
    }

    /**
     * Неожиданный элемент.
     *
     * @param fileName    имя файла
     * @param elementName имя неожиданного элемента
     * @param location    позиция в XML (может быть {@code null})
     * @return готовое исключение
     */
    public static InvalidFormatException unexpectedElement(String fileName, String elementName,
                                                           Location location) {
        return new InvalidFormatException(
                "Unexpected element: <" + elementName + ">",
                fileName,
                location != null ? location.getLineNumber() : -1,
                location != null ? location.getColumnNumber() : -1,
                elementName
        );
    }

    /**
     * Неожиданный элемент с контекстом (где именно он не ожидался).
     *
     * @param fileName      имя файла
     * @param elementName   имя неожиданного элемента
     * @param parentElement имя родительского элемента, внутри которого он встретился
     * @param location      позиция в XML (может быть {@code null})
     * @return готовое исключение
     */
    public static InvalidFormatException unexpectedElement(String fileName, String elementName,
                                                           String parentElement, Location location) {
        return new InvalidFormatException(
                "Unexpected element <" + elementName + "> inside <" + parentElement + ">",
                fileName,
                location != null ? location.getLineNumber() : -1,
                location != null ? location.getColumnNumber() : -1,
                elementName
        );
    }

    /**
     * Дубликат элемента, который должен присутствовать в единственном экземпляре.
     *
     * @param fileName    имя файла
     * @param elementName имя продублированного элемента
     * @param location    позиция в XML (может быть {@code null})
     * @return готовое исключение
     */
    public static InvalidFormatException duplicateElement(String fileName, String elementName,
                                                          Location location) {
        return new InvalidFormatException(
                "Duplicate element: <" + elementName + "> (only one is allowed)",
                fileName,
                location != null ? location.getLineNumber() : -1,
                location != null ? location.getColumnNumber() : -1,
                elementName
        );
    }

    /**
     * Неподдерживаемая версия формата.
     *
     * @param fileName имя файла
     * @param version  обнаруженная (неподдерживаемая) версия
     * @return готовое исключение
     */
    public static InvalidFormatException unsupportedVersion(String fileName, String version) {
        return new InvalidFormatException(
                "Unsupported FictionBook version: " + version,
                fileName, -1, -1, null
        );
    }

    /**
     * Недопустимая ссылка (например, на несуществующий якорь).
     *
     * @param fileName    имя файла
     * @param href        значение ссылки, которое не удалось разрешить
     * @param elementName имя элемента, содержащего ссылку
     * @param location    позиция в XML (может быть {@code null})
     * @return готовое исключение
     */
    public static InvalidFormatException brokenReference(String fileName, String href,
                                                         String elementName, Location location) {
        return new InvalidFormatException(
                "Broken reference '" + href + "' in element <" + elementName + ">: target not found",
                fileName,
                location != null ? location.getLineNumber() : -1,
                location != null ? location.getColumnNumber() : -1,
                elementName
        );
    }

    /**
     * Повреждённый или недоступный ресурс.
     *
     * @param fileName   имя файла
     * @param resourceId идентификатор проблемного ресурса
     * @param reason     причина недоступности/повреждения
     * @return готовое исключение
     */
    public static InvalidFormatException brokenResource(String fileName, String resourceId,
                                                        String reason) {
        return new InvalidFormatException(
                "Broken resource '" + resourceId + "': " + reason,
                fileName, -1, -1, null
        );
    }

    /**
     * Ошибка в ZIP-контейнере FB3.
     *
     * @param fileName имя файла
     * @param reason   описание проблемы с архивом
     * @param cause    исходное исключение-первопричина
     * @return готовое исключение
     */
    public static InvalidFormatException brokenArchive(String fileName, String reason, Throwable cause) {
        return new InvalidFormatException(
                "Corrupted FB3 archive: " + reason,
                cause, fileName, -1, -1, null
        );
    }

    /**
     * Отсутствует обязательный файл внутри FB3-архива.
     *
     * @param fileName  имя файла
     * @param entryPath путь к отсутствующей записи внутри архива
     * @return готовое исключение
     */
    public static InvalidFormatException missingFb3Entry(String fileName, String entryPath) {
        return new InvalidFormatException(
                "Required entry is missing in FB3 archive: " + entryPath,
                fileName, -1, -1, null
        );
    }

    /**
     * Общая ошибка валидации с произвольным сообщением.
     *
     * @param fileName имя файла
     * @param message  человекочитаемое описание ошибки
     * @param location позиция в XML (может быть {@code null})
     * @return готовое исключение
     */
    public static InvalidFormatException validationError(String fileName, String message,
                                                         Location location) {
        return new InvalidFormatException(
                message,
                fileName,
                location != null ? location.getLineNumber() : -1,
                location != null ? location.getColumnNumber() : -1,
                null
        );
    }

    // ========================================================================
    // УТИЛИТЫ
    // ========================================================================

    /**
     * Извлекает имя файла из {@link Path} для использования в сообщениях об ошибках.
     *
     * @param path путь к файлу (может быть {@code null})
     * @return короткое имя файла, либо полный путь, либо {@code null} для {@code null}-входа
     */
    public static String extractFileName(Path path) {
        if (path == null) return null;
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }
}
