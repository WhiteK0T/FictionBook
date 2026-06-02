package org.tehlab.whitek0t.fictionbook.internal.parser.jackson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.description.*;
import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;
import org.tehlab.whitek0t.fictionbook.internal.parser.stax.Fb2BlockParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Преобразует внутренние Jax-классы Jackson в публичные DTO Records.
 */
public class DescriptionMapper {

    private static final Logger log = LoggerFactory.getLogger(DescriptionMapper.class);

    private final Fb2BlockParser blockParser;

    public DescriptionMapper(Fb2BlockParser blockParser) {
        this.blockParser = blockParser;
    }

    public Description toDto(Fb2DescriptionJax jax) {
        if (jax == null) return null;

        return new Description(
                toTitleInfo(jax.titleInfo),
                toDocumentInfo(jax.documentInfo),
                toPublishInfo(jax.publishInfo)
        );
    }

    // ========================================================================
    // TITLE-INFO
    // ========================================================================

    private TitleInfo toTitleInfo(Fb2TitleInfoJax jax) {
        if (jax == null) return null;

        List<Author> authors = jax.authors != null
                ? jax.authors.stream().map(this::toAuthor).toList()
                : List.of();

        List<String> genres = jax.genres != null ? List.copyOf(jax.genres) : List.of();

        List<BlockElement> annotation = parseAnnotation(jax.annotation);

        Sequence sequence = jax.sequence != null
                ? new Sequence(jax.sequence.name, jax.sequence.number)
                : null;

        List<String> coverImageIds = extractCoverImageIds(jax.coverpage);

        return new TitleInfo(
                authors,
                genres,
                jax.bookTitle,
                annotation,
                jax.lang,
                jax.srcLang,
                sequence,
                coverImageIds
        );
    }

    private Author toAuthor(Fb2AuthorJax jax) {
        if (jax == null) return null;
        return new Author(jax.firstName, jax.middleName, jax.lastName);
    }

    /**
     * Парсит аннотацию из сырого XML-текста.
     */
    private List<BlockElement> parseAnnotation(Fb2AnnotationJax annotation) {
        if (annotation == null || annotation.rawXml == null || annotation.rawXml.isBlank()) {
            return List.of();
        }

        try {
            return blockParser.parseXmlFragment(annotation.rawXml);
        } catch (Exception e) {
            log.warn("Failed to parse annotation, falling back to plain text", e);
            // Fallback: оборачиваем сырой текст в параграф
            String plainText = stripXmlTags(annotation.rawXml);
            if (!plainText.isBlank()) {
                return List.of(new Paragraph(List.of(new Text(plainText))));
            }
            return List.of();
        }
    }

    /**
     * Извлекает ID обложек из <coverpage>.
     */
    private List<String> extractCoverImageIds(Fb2CoverpageJax coverpage) {
        if (coverpage == null || coverpage.images == null) {
            return List.of();
        }

        return coverpage.images.stream()
                .map(img -> {
                    String href = img.getEffectiveHref();  // ✅ Используем fallback-метод
                    if (href == null) return null;
                    return href.startsWith("#") ? href.substring(1) : href;
                })
                .filter(id -> id != null && !id.isBlank())
                .toList();
    }

    // ========================================================================
    // DOCUMENT-INFO
    // ========================================================================

    private DocumentInfo toDocumentInfo(Fb2DocumentInfoJax jax) {
        if (jax == null) return null;

        List<Author> authors = jax.authors != null
                ? jax.authors.stream().map(this::toAuthor).toList()
                : List.of();

        List<String> history = parseHistory(jax.history);

        return new DocumentInfo(
                authors,
                jax.programUsed,
                jax.date,
                jax.srcUrl,
                jax.srcOcr,
                jax.id,
                jax.version,
                history
        );
    }

    /**
     * Парсит историю документа из сырого XML-текста.
     * Возвращает список строк (каждая строка — один параграф или запись истории).
     */
    private List<String> parseHistory(Fb2HistoryJax history) {
        if (history == null || history.rawXml == null || history.rawXml.isBlank()) {
            return List.of();
        }

        try {
            List<BlockElement> blocks = blockParser.parseXmlFragment(history.rawXml);
            List<String> result = new ArrayList<>();

            for (BlockElement block : blocks) {
                if (block instanceof Paragraph p) {
                    String text = extractTextFromParagraph(p);
                    if (!text.isBlank()) {
                        result.add(text);
                    }
                }
            }

            return List.copyOf(result);

        } catch (Exception e) {
            log.warn("Failed to parse history, falling back to plain text", e);
            // Fallback: убираем XML-теги и возвращаем как есть
            String plainText = stripXmlTags(history.rawXml);
            if (!plainText.isBlank()) {
                return List.of(plainText);
            }
            return List.of();
        }
    }

    /**
     * Извлекает текст из параграфа, сохраняя форматирование в виде plain text.
     * Пример: "Обычный <strong>жирный</strong> текст" → "Обычный жирный текст"
     */
    private String extractTextFromParagraph(Paragraph paragraph) {
        StringBuilder sb = new StringBuilder();
        for (InlineElement inline : paragraph.elements()) {
            extractTextRecursive(inline, sb);
        }
        return sb.toString().trim();
    }

    /**
     * Рекурсивно извлекает текст из инлайн-элементов.
     */
    private void extractTextRecursive(InlineElement inline, StringBuilder sb) {
        if (inline instanceof Text t) {
            sb.append(t.value());
        } else if (inline instanceof org.tehlab.whitek0t.fictionbook.dto.inline.Strong s) {
            s.elements().forEach(e -> extractTextRecursive(e, sb));
        } else if (inline instanceof org.tehlab.whitek0t.fictionbook.dto.inline.Emphasis e) {
            e.elements().forEach(el -> extractTextRecursive(el, sb));
        } else if (inline instanceof org.tehlab.whitek0t.fictionbook.dto.inline.Link l) {
            l.elements().forEach(el -> extractTextRecursive(el, sb));
        }
        // Другие типы (Sub, Sup, Strikethrough, ImageRef) игнорируем или обрабатываем по необходимости
    }

    // ========================================================================
    // PUBLISH-INFO
    // ========================================================================

    private PublishInfo toPublishInfo(Fb2PublishInfoJax jax) {
        if (jax == null) return null;

        return new PublishInfo(
                jax.bookName,
                jax.publisher,
                jax.city,
                jax.year,
                jax.isbn
        );
    }

    // ========================================================================
    // УТИЛИТЫ
    // ========================================================================

    /**
     * Убирает XML-теги из строки, оставляя только текст.
     * Используется как fallback при ошибках парсинга.
     */
    private String stripXmlTags(String xml) {
        if (xml == null) return "";
        return xml.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
