package org.tehlab.whitek0t.fictionbook.internal.reader.fb3;

import org.tehlab.whitek0t.fictionbook.api.FictionBookStreamer;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.Section;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.exception.InvalidFormatException;
import org.tehlab.whitek0t.fictionbook.internal.anchor.AnchorIndex;
import org.tehlab.whitek0t.fictionbook.internal.anchor.AnchorIndexBuilder;
import org.tehlab.whitek0t.fictionbook.internal.parser.stax.Fb2BlockParser;

import javax.xml.stream.XMLInputFactory;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Потоковая FB3-реализация {@link FictionBookStreamer}.
 *
 * <p><b>Гарантия:</b> <i>ленивые секции и ленивый контейнер.</i> Архив открывается
 * через {@code ZipFile} ({@link Fb3Package#openLazy}) — части читаются по требованию,
 * а не разжимаются в память целиком: {@link #readNextSection()} стримит {@code body.xml}
 * прямо из ZIP и отдаёт по одной секции верхнего уровня, а картинки ({@link #getResource})
 * читаются из архива лишь при обращении. Контейнер держится открытым до {@link #close()}.</p>
 *
 * <p><b>Ограничения v1</b> (как и у {@code Fb2Streamer}):</p>
 * <ul>
 *   <li>Секции тела сносок отдаются после основных, в одном потоке: {@link Section}
 *       не несёт имени тела.</li>
 *   <li>{@link #buildAnchorIndex()} читает книгу целиком — это не потоковая операция.</li>
 *   <li>{@link Resource}-ы валидны, пока стример открыт: их данные читаются из ZIP,
 *       который закрывается в {@link #close()}.</li>
 * </ul>
 */
public final class Fb3Streamer implements FictionBookStreamer {

    private enum Phase {MAIN, NOTES, DONE}

    private final Path file;
    private final String fileName;
    private final Fb3Package pkg;
    private final Fb3Layout layout;
    private final Fb3DescriptionParser descriptionParser;
    private final Fb3BodyParser bodyParser;

    private Description description;
    private boolean descriptionConsumed;

    private Phase phase = Phase.MAIN;
    private Fb3BodyParser.SectionCursor cursor;

    /**
     * Открывает FB3-файл для потокового чтения (распаковывает контейнер, резолвит раскладку).
     *
     * @param file путь к {@code .fb3}-файлу
     * @throws FictionBookException при ошибке открытия/распаковки архива
     */
    public Fb3Streamer(Path file) throws FictionBookException {
        this.file = file;
        this.fileName = InvalidFormatException.extractFileName(file);

        XMLInputFactory factory = newStaxFactory();
        Fb2BlockParser blockParser = new Fb2BlockParser(factory);
        this.descriptionParser = new Fb3DescriptionParser(factory, blockParser);
        this.bodyParser = new Fb3BodyParser(factory, blockParser);

        // Ленивый контейнер: картинки/тело читаются по требованию из открытого ZIP,
        // не загружая весь архив в память (закрывается в close()).
        this.pkg = Fb3Package.openLazy(file, fileName, factory);
        this.layout = Fb3Layout.resolve(pkg, factory);
    }

    private static XMLInputFactory newStaxFactory() {
        XMLInputFactory f = XMLInputFactory.newInstance();
        f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_COALESCING, false);
        return f;
    }

    @Override
    public Description readDescription() throws FictionBookException {
        if (!descriptionConsumed) {
            descriptionConsumed = true;
            byte[] descBytes = pkg.get(layout.descPart);
            if (descBytes == null) {
                throw InvalidFormatException.missingFb3Entry(fileName, layout.descPart);
            }
            description = descriptionParser.parse(descBytes, fileName, layout.coverIds);
        }
        return description;
    }

    @Override
    public Section readNextSection() throws FictionBookException {
        while (phase != Phase.DONE) {
            if (cursor == null) {
                cursor = openCursorForPhase();
                if (cursor == null) {
                    advancePhase();
                    continue;
                }
            }
            Section section = cursor.next();
            if (section != null) {
                return Fb3Layout.rewriteImageHrefs(section, layout.hrefToId());
            }
            closeCursor();
            advancePhase();
        }
        return null;
    }

    @Override
    public Resource getResource(String id) {
        if (id == null) {
            return null;
        }
        String key = id.startsWith("#") ? id.substring(1) : id;
        return layout.resources().get(key);
    }

    @Override
    public AnchorIndex buildAnchorIndex() throws FictionBookException {
        // Индекс якорей по природе охватывает всю книгу — читаем её целиком.
        return AnchorIndexBuilder.fromDto(new Fb3Reader().read(file));
    }

    @Override
    public void close() throws Exception {
        try {
            closeCursor();
        } finally {
            pkg.close();
        }
    }

    // ========================================================================
    // ВНУТРЕННЕЕ
    // ========================================================================

    /** Открывает курсор секций для текущей фазы; {@code null}, если соответствующей части нет. */
    private Fb3BodyParser.SectionCursor openCursorForPhase() throws FictionBookException {
        String part = switch (phase) {
            case MAIN -> layout.bodyPart;
            case NOTES -> layout.notesPart;
            case DONE -> null;
        };
        if (part == null || !pkg.has(part)) {
            return null;
        }
        try {
            // Тело стримится прямо из части архива — без буферизации body.xml целиком.
            return bodyParser.cursor(pkg.openPart(part), fileName);
        } catch (IOException e) {
            throw new FictionBookException("Failed to open FB3 body part '" + part + "' in " + fileName, e);
        }
    }

    private void advancePhase() {
        phase = switch (phase) {
            case MAIN -> Phase.NOTES;
            case NOTES, DONE -> Phase.DONE;
        };
    }

    private void closeCursor() throws FictionBookException {
        if (cursor != null) {
            try {
                cursor.close();
            } catch (Exception e) {
                throw new FictionBookException("Failed to close FB3 body cursor in " + fileName, e);
            } finally {
                cursor = null;
            }
        }
    }
}
