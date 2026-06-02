package org.tehlab.whitek0t.fictionbook.render;

import org.tehlab.whitek0t.fictionbook.dto.Resource;

/**
 * Резолвер ресурсов (картинок) для рендереров.
 *
 * <p>Разные рендереры по-разному работают с картинками:</p>
 * <ul>
 *   <li>HTML — нужна ссылка на файл или base64 data URI</li>
 *   <li>PlainText — только alt-текст</li>
 *   <li>JavaFX — нужно получить byte[] для Image</li>
 * </ul>
 *
 * <p>Пример реализации для HTML с сохранением картинок в папку:</p>
 * <pre>{@code
 * ResourceResolver resolver = (resource, alt) -> {
 *     Path imgPath = imagesDir.resolve(resource.id() + ".jpg");
 *     try (InputStream is = resource.dataProvider().getInputStream();
 *          OutputStream os = Files.newOutputStream(imgPath)) {
 *         is.transferTo(os);
 *     }
 *     return "images/" + resource.id() + ".jpg";
 * };
 * }</pre>
 */
@FunctionalInterface
public interface ResourceResolver {

    /**
     * Разрешает ресурс в строковое представление для рендерера.
     *
     * @param resource ресурс (может быть null, если ссылка битая)
     * @param alt      альтернативный текст
     * @return строка для вставки в рендер (URL, data URI, путь) или null
     */
    String resolve(Resource resource, String alt);

    /**
     * Резолвер по умолчанию: возвращает placeholder.
     */
    static ResourceResolver placeholder() {
        return (resource, alt) -> "[image: " + (alt != null ? alt : "no alt") + "]";
    }

    /**
     * Резолвер, генерирующий base64 data URI (картинка встраивается в HTML).
     * Подходит для маленьких книг, где не хочется возиться с отдельными файлами.
     */
    static ResourceResolver base64DataUri() {
        return (resource, alt) -> {
            if (resource == null) return null;
            try (java.io.InputStream is = resource.dataProvider().getInputStream()) {
                byte[] bytes = is.readAllBytes();
                String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
                return "data:" + resource.contentType() + ";base64," + base64;
            } catch (java.io.IOException e) {
                return null;
            }
        };
    }

    /**
     * Резолвер, сохраняющий картинки в указанную папку и возвращающий относительный URL.
     *
     * @param imagesDir         папка для сохранения картинок
     * @param relativeUrlPrefix префикс URL (например, "images/")
     */
    static ResourceResolver saveToDirectory(java.nio.file.Path imagesDir, String relativeUrlPrefix) {
        return (resource, alt) -> {
            if (resource == null) return null;
            try {
                java.nio.file.Files.createDirectories(imagesDir);
                String ext = org.tehlab.whitek0t.fictionbook.util.MimeTypeResolver
                        .toExtension(resource.contentType());
                java.nio.file.Path imgPath = imagesDir.resolve(resource.id() + ext);

                try (java.io.InputStream is = resource.dataProvider().getInputStream();
                     java.io.OutputStream os = java.nio.file.Files.newOutputStream(imgPath)) {
                    is.transferTo(os);
                }

                return relativeUrlPrefix + resource.id() + ext;
            } catch (java.io.IOException e) {
                return null;
            }
        };
    }
}
