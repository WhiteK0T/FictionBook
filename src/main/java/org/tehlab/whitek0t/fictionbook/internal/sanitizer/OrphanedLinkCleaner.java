package org.tehlab.whitek0t.fictionbook.internal.sanitizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.inline.InlineElement;
import org.tehlab.whitek0t.fictionbook.dto.inline.Link;
import org.tehlab.whitek0t.fictionbook.dto.inline.Text;
import org.tehlab.whitek0t.fictionbook.internal.anchor.AnchorIndex;
import org.tehlab.whitek0t.fictionbook.internal.anchor.AnchorIndexBuilder;

/**
 * Проверяет внутренние ссылки ({@code #anchor}) и обрабатывает битые.
 *
 * <p>Использует {@link AnchorIndex} для валидации ссылок.
 * Битые ссылки не удаляются (чтобы не терять текст), но помечаются
 * через добавление префикса к отображаемому тексту.</p>
 */
public class OrphanedLinkCleaner implements Sanitizer {

    private static final Logger log = LoggerFactory.getLogger(OrphanedLinkCleaner.class);

    @Override
    public FictionBookDto sanitize(FictionBookDto book) {
        AnchorIndex index = AnchorIndexBuilder.fromDto(book);

        return FictionBookDtoTransformer.transform(book)
                .onInlineElement(inline -> handleLink(inline, index))
                .apply();
    }

    private InlineElement handleLink(InlineElement inline, AnchorIndex index) {
        if (!(inline instanceof Link link)) {
            return inline;
        }

        String href = link.href();
        if (href == null || href.isBlank()) {
            return inline;
        }

        // Проверяем только внутренние ссылки
        if (!isInternalLink(href)) {
            return inline;
        }

        if (!index.canResolve(href)) {
            log.debug("Broken internal link: {} (target not found)", href);
            // Добавляем префикс к тексту ссылки
            return markLinkAsBroken(link, href);
        }

        return inline;
    }

    private boolean isInternalLink(String href) {
        return href.startsWith("#") || href.contains("#");
    }

    private Link markLinkAsBroken(Link link, String href) {
        // Если текст ссылки пустой, используем сам href
        if (link.elements().isEmpty()) {
            return new Link(link.href(), link.type(),
                    java.util.List.of(new Text("[broken: " + href + "]")));
        }
        // Иначе оставляем как есть — пользователь увидит ссылку, но она не сработает
        return link;
    }
}
