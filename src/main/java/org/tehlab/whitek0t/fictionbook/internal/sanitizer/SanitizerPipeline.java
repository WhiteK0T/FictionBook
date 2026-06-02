package org.tehlab.whitek0t.fictionbook.internal.sanitizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;

import java.util.ArrayList;
import java.util.List;

/**
 * Цепочка санитайзеров. Применяет их последовательно.
 *
 * <p>Пример использования:</p>
 * <pre>{@code
 * SanitizerPipeline pipeline = SanitizerPipeline.builder()
 *     .add(new EmptyParagraphCleaner())
 *     .add(new TextNodeMerger())
 *     .add(new OrphanedImageCleaner())
 *     .add(new OrphanedLinkCleaner())
 *     .add(new EmptySectionCleaner())
 *     .build();
 *
 * FictionBookDto clean = pipeline.sanitize(book);
 * }</pre>
 */
public class SanitizerPipeline implements Sanitizer {

    private static final Logger log = LoggerFactory.getLogger(SanitizerPipeline.class);

    private final List<Sanitizer> sanitizers;

    private SanitizerPipeline(List<Sanitizer> sanitizers) {
        this.sanitizers = List.copyOf(sanitizers);
    }

    /**
     * Создаёт стандартный пайплайн со всеми базовыми санитайзерами.
     * Порядок применения важен!
     */
    public static SanitizerPipeline standard() {
        return builder()
                // Сначала чистим пустые узлы
                .add(new EmptyParagraphCleaner())
                .add(new EmptySectionCleaner())
                // Затем нормализуем текст (склейка разбитых нод)
                .add(new TextNodeMerger())
                // Проверяем ссылки (требует AnchorIndex)
                .add(new OrphanedImageCleaner())
                .add(new OrphanedLinkCleaner())
                // В конце — нормализация атрибутов
                .add(new AttributeNormalizer())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public FictionBookDto sanitize(FictionBookDto book) {
        if (book == null) return null;

        FictionBookDto current = book;
        for (Sanitizer sanitizer : sanitizers) {
            try {
                FictionBookDto before = current;
                current = sanitizer.sanitize(current);

                if (current == null) {
                    log.warn("Sanitizer {} returned null, using previous state",
                            sanitizer.getClass().getSimpleName());
                    current = before;
                }
            } catch (Exception e) {
                log.error("Sanitizer {} failed, skipping",
                        sanitizer.getClass().getSimpleName(), e);
                // Продолжаем со следующим санитайзером
            }
        }
        return current;
    }

    public static class Builder {
        private final List<Sanitizer> sanitizers = new ArrayList<>();

        public Builder add(Sanitizer sanitizer) {
            if (sanitizer != null) {
                sanitizers.add(sanitizer);
            }
            return this;
        }

        public SanitizerPipeline build() {
            return new SanitizerPipeline(sanitizers);
        }
    }
}
