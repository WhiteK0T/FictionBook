package org.tehlab.whitek0t.fictionbook.internal.reader.fb3;

import org.tehlab.whitek0t.fictionbook.dto.BodyDto;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.description.Description;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.exception.InvalidFormatException;
import org.tehlab.whitek0t.fictionbook.internal.parser.stax.Fb2BlockParser;

import javax.xml.stream.XMLInputFactory;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Читает FB3-файл (OPC/ZIP-контейнер) в {@link FictionBookDto}.
 *
 * <p>Поток разбора: распаковка архива ({@link Fb3Package}) → навигация по OPC-связям
 * {@code _rels/*.rels} ({@link Fb3Layout}: книга → {@code description.xml} →
 * {@code body.xml} + картинки) → разбор {@code description.xml}
 * ({@link Fb3DescriptionParser}) и {@code body.xml} ({@link Fb3BodyParser}) →
 * регистрация картинок как {@code Resource} и переписывание ссылок {@code <img>}
 * на якоря вида {@code "#id"}, чтобы рендеринг работал единообразно с FB2.</p>
 *
 * <p>Как и FB2-ридер, режим прощающий: при отсутствии {@code .rels} используются
 * стандартные пути ({@code fb3/description.xml}, {@code fb3/body.xml}); неизвестные
 * элементы пропускаются.</p>
 */
public class Fb3Reader {

    private final XMLInputFactory factory;
    private final Fb2BlockParser blockParser;
    private final Fb3DescriptionParser descriptionParser;
    private final Fb3BodyParser bodyParser;

    public Fb3Reader() {
        this.factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, false);

        this.blockParser = new Fb2BlockParser(factory);
        this.descriptionParser = new Fb3DescriptionParser(factory, blockParser);
        this.bodyParser = new Fb3BodyParser(factory, blockParser);
    }

    /**
     * Читает FB3-файл по указанному пути.
     *
     * @param file путь к {@code .fb3}-файлу
     * @return разобранная книга
     * @throws FictionBookException при ошибке распаковки или разбора
     */
    public FictionBookDto read(Path file) throws FictionBookException {
        String fileName = InvalidFormatException.extractFileName(file);
        return build(Fb3Package.open(file, fileName, factory), fileName);
    }

    /**
     * Читает FB3 из потока (для тестов / нестандартных источников).
     *
     * @param in поток с данными {@code .fb3}-архива
     * @return разобранная книга
     * @throws FictionBookException при ошибке распаковки или разбора
     */
    public FictionBookDto read(InputStream in) throws FictionBookException {
        return build(Fb3Package.open(in, "<stream>", factory), "<stream>");
    }

    // ========================================================================
    // ОСНОВНОЙ КОНВЕЙЕР
    // ========================================================================

    private FictionBookDto build(Fb3Package pkg, String fileName) throws FictionBookException {
        Fb3Layout layout = Fb3Layout.resolve(pkg, factory);

        byte[] descBytes = pkg.get(layout.descPart);
        if (descBytes == null) {
            throw InvalidFormatException.missingFb3Entry(fileName, layout.descPart);
        }
        byte[] bodyBytes = pkg.get(layout.bodyPart);
        if (bodyBytes == null) {
            throw InvalidFormatException.missingFb3Entry(fileName, layout.bodyPart);
        }

        // Описание и тело
        Description description = descriptionParser.parse(descBytes, fileName, layout.coverIds);

        List<BodyDto> bodies = new ArrayList<>(bodyParser.parse(bodyBytes, null, fileName));
        if (layout.notesPart != null) {
            byte[] notesBytes = pkg.get(layout.notesPart);
            if (notesBytes != null) {
                bodies.addAll(bodyParser.parse(notesBytes, "notes", fileName));
            }
        }
        if (bodies.isEmpty()) {
            throw InvalidFormatException.missingElement(fileName, "fb3-body");
        }

        // Картинки → ресурсы + переписывание ссылок <img> на якоря "#id"
        FictionBookDto dto = new FictionBookDto(description, bodies, layout.resources());
        return Fb3Layout.rewriteImageHrefs(dto, layout.hrefToId());
    }
}
