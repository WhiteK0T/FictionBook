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
import java.nio.file.Path;

/**
 * Потоковая FB3-реализация {@link FictionBookStreamer}.
 *
 * <p><b>Гарантия:</b> <i>ленивые секции, eager-контейнер.</i> FB3 — это OPC/ZIP-архив
 * с дефлейтом, поэтому seek по нему невозможен и контейнер распаковывается в память
 * целиком ({@link Fb3Package}), как и в {@link Fb3Reader}. Главный выигрыш стримера —
 * не строить разом полное дерево секций (самую тяжёлую часть DTO): {@link #readNextSection()}
 * разбирает и отдаёт по одной секции верхнего уровня из {@code body.xml}, не удерживая
 * предыдущие.</p>
 *
 * <p><b>Ограничения v1</b> (как и у {@code Fb2Streamer}):</p>
 * <ul>
 *   <li>Секции тела сносок отдаются после основных, в одном потоке: {@link Section}
 *       не несёт имени тела.</li>
 *   <li>{@link #buildAnchorIndex()} читает книгу целиком — это не потоковая операция.</li>
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

        this.pkg = Fb3Package.open(file, fileName, factory);
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
        closeCursor();
    }

    // ========================================================================
    // ВНУТРЕННЕЕ
    // ========================================================================

    /** Открывает курсор секций для текущей фазы; {@code null}, если соответствующей части нет. */
    private Fb3BodyParser.SectionCursor openCursorForPhase() throws FictionBookException {
        byte[] bytes = switch (phase) {
            case MAIN -> pkg.get(layout.bodyPart);
            case NOTES -> layout.notesPart == null ? null : pkg.get(layout.notesPart);
            case DONE -> null;
        };
        return bytes == null ? null : bodyParser.cursor(bytes, fileName);
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
