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

    private final String fileName;
    private final int line;
    private final int column;
    private final String elementName;

    /**
     * Базовый конструктор.
     */
    public InvalidFormatException(String message) {
        super(message);
        this.fileName = null;
        this.line = -1;
        this.column = -1;
        this.elementName = null;
    }

    /**
     * Конструктор с причиной.
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
     */
    public InvalidFormatException(String message, String fileName, int line, int column, String elementName) {
        super(message);
        this.fileName = fileName;
        this.line = line;
        this.column = column;
        this.elementName = elementName;
    }

    /**
     * Полный конструктор с причиной и информацией о месте ошибки.
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
     * @return Имя файла, в котором обнаружена ошибка (или null, если неизвестно)
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * @return Номер строки с ошибкой (или -1, если неизвестно)
     */
    public int getLine() {
        return line;
    }

    /**
     * @return Номер колонки с ошибкой (или -1, если неизвестно)
     */
    public int getColumn() {
        return column;
    }

    /**
     * @return Имя XML-элемента, в котором обнаружена ошибка (или null)
     */
    public String getElementName() {
        return elementName;
    }

    /**
     * @return true, если есть информация о месте ошибки
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
     */
    public static InvalidFormatException missingElement(String fileName, String elementName) {
        return new InvalidFormatException(
                "Required element is missing: <" + elementName + ">",
                fileName, -1, -1, elementName
        );
    }

    /**
     * Отсутствует обязательный элемент (с позицией).
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
     * Неожиданный элемент с контекстом (где не ожидался).
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
     * Дубликат элемента (если запрещён).
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
     */
    public static InvalidFormatException unsupportedVersion(String fileName, String version) {
        return new InvalidFormatException(
                "Unsupported FictionBook version: " + version,
                fileName, -1, -1, null
        );
    }

    /**
     * Недопустимая ссылка (например, на несуществующий якорь).
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
     */
    public static InvalidFormatException brokenArchive(String fileName, String reason, Throwable cause) {
        return new InvalidFormatException(
                "Corrupted FB3 archive: " + reason,
                cause, fileName, -1, -1, null
        );
    }

    /**
     * Отсутствует обязательный файл внутри FB3-архива.
     */
    public static InvalidFormatException missingFb3Entry(String fileName, String entryPath) {
        return new InvalidFormatException(
                "Required entry is missing in FB3 archive: " + entryPath,
                fileName, -1, -1, null
        );
    }

    /**
     * Общая ошибка валидации с произвольным сообщением.
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
     * Извлекает имя файла из Path для использования в сообщениях об ошибках.
     */
    public static String extractFileName(Path path) {
        if (path == null) return null;
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }
}