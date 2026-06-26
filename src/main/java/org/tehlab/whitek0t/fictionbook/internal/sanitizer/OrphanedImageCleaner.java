package org.tehlab.whitek0t.fictionbook.internal.sanitizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;
import org.tehlab.whitek0t.fictionbook.dto.block.BlockImage;
import org.tehlab.whitek0t.fictionbook.dto.block.Paragraph;
import org.tehlab.whitek0t.fictionbook.dto.inline.ImageRef;
import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;

import java.util.List;
import java.util.Set;

/**
 * Обрабатывает ссылки на несуществующие ресурсы (картинки).
 *
 * <p>Если {@code <image l:href="#cover"/>} ссылается на несуществующий
 * бинарник, заменяет ссылку на текстовую метку {@code [Image Missing: #cover]} —
 * для inline ({@link ImageRef}) и для блочной ({@link BlockImage}, заворачивается
 * в {@code <p>}) картинки.</p>
 */
public class OrphanedImageCleaner implements Sanitizer {

    private static final Logger log = LoggerFactory.getLogger(OrphanedImageCleaner.class);

    @Override
    public FictionBookDto sanitize(FictionBookDto book) {
        Set<String> existingIds = book.resources().keySet();

        return FictionBookDtoTransformer.transform(book)
                .onInlineElement(inline -> handleImageRef(inline, existingIds))
                .onBlockElement(block -> handleBlockImage(block, existingIds))
                .apply();
    }

    private BlockElement handleBlockImage(BlockElement block, Set<String> existingIds) {
        if (!(block instanceof BlockImage img)) {
            return block;
        }

        String href = img.href();
        if (href == null) {
            log.warn("BlockImage without href, replacing with placeholder");
            return new Paragraph(List.of(new Text("[Image]")));
        }

        String id = extractIdFromHref(href);
        if (id == null) {
            // Внешняя ссылка — не трогаем
            return block;
        }

        if (!existingIds.contains(id)) {
            log.debug("Broken block image reference: {} (not found in resources)", href);
            return new Paragraph(List.of(new Text("[Image Missing: " + href + "]")));
        }

        return block;
    }

    private InlineElement handleImageRef(InlineElement inline, Set<String> existingIds) {
        if (!(inline instanceof ImageRef img)) {
            return inline;
        }

        String href = img.href();
        if (href == null) {
            log.warn("ImageRef without href, replacing with placeholder");
            return new Text("[Image]");
        }

        String id = extractIdFromHref(href);
        if (id == null) {
            // Внешняя ссылка — не трогаем
            return inline;
        }

        if (!existingIds.contains(id)) {
            log.debug("Broken image reference: {} (not found in resources)", href);
            return new Text("[Image Missing: " + href + "]");
        }

        return inline;
    }

    private String extractIdFromHref(String href) {
        if (href.startsWith("#")) {
            return href.substring(1);
        }
        int hashIndex = href.indexOf('#');
        if (hashIndex >= 0) {
            return href.substring(hashIndex + 1);
        }
        return null;
    }
}
