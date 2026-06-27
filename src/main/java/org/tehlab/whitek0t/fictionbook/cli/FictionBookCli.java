package org.tehlab.whitek0t.fictionbook.cli;

import org.tehlab.whitek0t.fictionbook.api.BookInfo;
import org.tehlab.whitek0t.fictionbook.api.FictionBookFormat;
import org.tehlab.whitek0t.fictionbook.api.FictionBookIO;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.dto.block.BlockElement;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.render.BookPlayer;
import org.tehlab.whitek0t.fictionbook.render.FictionBookRenderer;
import org.tehlab.whitek0t.fictionbook.render.ParagraphStyle;
import org.tehlab.whitek0t.fictionbook.render.ResourceResolver;
import org.tehlab.whitek0t.fictionbook.render.impl.HtmlRenderer;
import org.tehlab.whitek0t.fictionbook.render.impl.MarkdownRenderer;
import org.tehlab.whitek0t.fictionbook.render.impl.PlainTextRenderer;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Консольная утилита конвертации книг FB2/FB3.
 *
 * <p>Поддержаны два направления вывода:</p>
 * <ul>
 *   <li><b>Рендеринг</b> в {@code txt}, {@code html}, {@code md} (через {@link BookPlayer}
 *       и рендереры);</li>
 *   <li><b>Книжные форматы</b> {@code fb2}, {@code fb3} — перезапись/конвертация
 *       {@code fb2↔fb3} через {@link FictionBookIO#write} (с авто-санитизацией).</li>
 * </ul>
 *
 * <p>Поддержан <b>пакетный режим</b>: на вход можно подать каталог (опц. {@code -r})
 * или несколько файлов; результаты складываются в {@code --out-dir} либо рядом
 * с каждым источником.</p>
 *
 * <p>Это единственная точка входа-приложение в проекте-библиотеке: она лишь связывает
 * публичные фасады и не добавляет новой логики разбора/рендеринга.</p>
 *
 * <p>Пример:</p>
 * <pre>{@code
 * fb book.fb2                       # FB2 -> текст (book.txt)
 * fb book.fb2 -f html              # FB2 -> HTML с встроенными картинками
 * fb book.fb3 -f fb2               # FB3 -> FB2 (book.fb2)
 * fb books/ -f fb3 -d out/         # пакет: все книги каталога -> FB3 в out/
 * fb book.fb2 -o -                 # в stdout (для пайпов; только txt/html/md)
 * }</pre>
 */
public final class FictionBookCli {

    private static final String VERSION = "1.0-SNAPSHOT";

    private FictionBookCli() {
    }

    /** Формат вывода: рендеринг (TXT/HTML/MD) или книжный формат (FB2/FB3). */
    private enum Target {TXT, HTML, MD, FB2, FB3}

    /** Режим обработки картинок при рендеринге в HTML/MD. */
    private enum ImageMode {EMBED, EXTRACT, NONE}

    /**
     * Точка входа JVM. Делегирует в {@link #run(String[], PrintStream, PrintStream)}
     * и пробрасывает её код возврата как код выхода процесса.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    /**
     * Выполняет конвертацию (одну или пакетную). Вынесено из {@link #main} ради
     * тестируемости: потоки вывода передаются явно, а результат — код возврата.
     *
     * @param args аргументы командной строки
     * @param out  поток для полезного вывода (например, при {@code -o -})
     * @param err  поток для сообщений и ошибок
     * @return код возврата: 0 — успех, 1 — ошибка чтения/записи, 2 — ошибка аргументов
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        // --- Разбор аргументов -------------------------------------------------
        List<String> positional = new ArrayList<>();
        Path output = null;
        Path outDir = null;
        Target format = null;
        ImageMode imageMode = ImageMode.EMBED;
        boolean wrapDocument = true;
        boolean recursive = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> {
                    printUsage(out);
                    return 0;
                }
                case "-v", "--version" -> {
                    out.println("fictionbook " + VERSION);
                    return 0;
                }
                case "-f", "--format" -> {
                    String value = next(args, ++i, err, arg);
                    if (value == null) return 2;
                    format = parseTarget(value, err);
                    if (format == null) return 2;
                }
                case "-o", "--output" -> {
                    String value = next(args, ++i, err, arg);
                    if (value == null) return 2;
                    output = Path.of(value);
                }
                case "-d", "--out-dir" -> {
                    String value = next(args, ++i, err, arg);
                    if (value == null) return 2;
                    outDir = Path.of(value);
                }
                case "-r", "--recursive" -> recursive = true;
                case "--images" -> {
                    String value = next(args, ++i, err, arg);
                    if (value == null) return 2;
                    imageMode = parseImageMode(value, err);
                    if (imageMode == null) return 2;
                }
                case "--no-wrap", "--fragment" -> wrapDocument = false;
                default -> {
                    if (arg.startsWith("-") && !arg.equals("-")) {
                        err.println("Неизвестная опция: " + arg);
                        printUsage(err);
                        return 2;
                    }
                    positional.add(arg);
                }
            }
        }

        if (positional.isEmpty()) {
            err.println("Не указан входной файл.");
            printUsage(err);
            return 2;
        }

        // Совместимость: устаревшая форма «fb <вход> <выход>» (два позиционных, без -o/-d,
        // первый — существующий файл, второй — ещё не существующий путь вывода).
        if (positional.size() == 2 && output == null && outDir == null
                && Files.isRegularFile(Path.of(positional.get(0)))
                && !Files.exists(Path.of(positional.get(1)))) {
            output = Path.of(positional.get(1));
            positional = List.of(positional.get(0));
        }

        // --- Раскрытие входов (файлы + каталоги) в список книг -----------------
        List<Path> books = new ArrayList<>();
        boolean sawDirectory = false;
        for (String s : positional) {
            Path p = Path.of(s);
            if (Files.isDirectory(p)) {
                sawDirectory = true;
                try {
                    collectBooks(p, recursive, books);
                } catch (IOException e) {
                    err.println("Ошибка обхода каталога '" + p + "': " + e.getMessage());
                    return 1;
                }
            } else if (Files.isRegularFile(p)) {
                books.add(p);
            } else {
                err.println("Файл не найден: " + p);
                return 1;
            }
        }
        if (books.isEmpty()) {
            err.println("Не найдено книг (.fb2/.fb3) для конвертации.");
            return 1;
        }

        boolean batch = books.size() > 1 || sawDirectory || outDir != null;
        return batch
                ? runBatch(books, outDir, format, imageMode, wrapDocument, output, out, err)
                : runSingle(books.getFirst(), output, format, imageMode, wrapDocument, out, err);
    }

    // ========================================================================
    // Режимы запуска
    // ========================================================================

    private static int runSingle(Path input, Path output, Target format, ImageMode imageMode,
                                 boolean wrap, PrintStream out, PrintStream err) {
        boolean toStdout = output != null && output.toString().equals("-");

        // Формат выводим только из имени ВЫХОДА (вход всегда .fb2/.fb3 — он не задаёт цель).
        if (format == null) {
            format = (output != null && !toStdout) ? inferTarget(output) : Target.TXT;
        }
        if (output == null) {
            output = deriveOutput(input, format);
        }
        return convertOne(input, output, toStdout, format, imageMode, wrap, out, err);
    }

    private static int runBatch(List<Path> books, Path outDir, Target format, ImageMode imageMode,
                                boolean wrap, Path output, PrintStream out, PrintStream err) {
        if (output != null) {
            err.println("В пакетном режиме используйте --out-dir, а не -o.");
            return 2;
        }
        Target fmt = format != null ? format : Target.TXT;

        int done = 0;
        int failed = 0;
        int skipped = 0;
        Set<Path> usedDests = new HashSet<>();
        for (Path book : books) {
            Path dest = batchOutput(book, outDir, fmt);
            if (sameFile(dest, book)) {
                err.println("Пропуск (выход совпадает с источником): " + book);
                skipped++;
                continue;
            }
            // Разные источники с одинаковым именем (напр. book.fb2 и book.fb3) дали бы
            // один путь вывода — добавляем расширение источника, чтобы не затирать.
            if (!usedDests.add(dest.toAbsolutePath().normalize())) {
                dest = disambiguate(dest, book, fmt);
                usedDests.add(dest.toAbsolutePath().normalize());
            }
            int code = convertOne(book, dest, false, fmt, imageMode, wrap, out, err);
            if (code == 0) {
                done++;
            } else {
                failed++;
            }
        }
        err.println("Пакет: успешно " + done + ", с ошибками " + failed
                + (skipped > 0 ? ", пропущено " + skipped : "")
                + " из " + books.size());
        return failed == 0 ? 0 : 1;
    }

    /**
     * Конвертирует одну книгу. Для {@code txt/html/md} — рендеринг в строку и запись;
     * для {@code fb2/fb3} — запись DTO через {@link FictionBookIO#write}.
     */
    private static int convertOne(Path input, Path output, boolean toStdout, Target format,
                                  ImageMode imageMode, boolean wrap, PrintStream out, PrintStream err) {
        FictionBookDto book;
        try {
            book = FictionBookIO.read(input);
        } catch (FictionBookException e) {
            err.println("Ошибка чтения '" + input + "': " + e.getMessage());
            return 1;
        }

        // Книжные форматы пишутся напрямую (бинарно/потоково), без рендеринга в строку.
        if (format == Target.FB2 || format == Target.FB3) {
            if (toStdout) {
                err.println("Формат " + name(format) + " нельзя выводить в stdout — укажите файл.");
                return 2;
            }
            FictionBookFormat target = format == Target.FB2 ? FictionBookFormat.FB2 : FictionBookFormat.FB3;
            try {
                ensureParent(output);
                FictionBookIO.write(book, output, target);
            } catch (Exception e) {
                err.println("Ошибка записи '" + output + "': " + e.getMessage());
                return 1;
            }
            err.println("Готово: " + input + " → " + output + " (" + name(format) + ")");
            return 0;
        }

        String rendered = switch (format) {
            case TXT -> renderText(book);
            case HTML -> renderHtml(book, output, toStdout, imageMode, wrap);
            case MD -> renderMarkdown(book, output, toStdout, imageMode);
            case FB2, FB3 -> throw new IllegalStateException("книжный формат обработан выше");
        };

        if (toStdout) {
            out.print(rendered);
            if (!rendered.endsWith("\n")) {
                out.println();
            }
            return 0;
        }

        try {
            ensureParent(output);
            Files.writeString(output, rendered, StandardCharsets.UTF_8);
        } catch (Exception e) {
            err.println("Ошибка записи '" + output + "': " + e.getMessage());
            return 1;
        }

        err.println("Готово: " + input + " → " + output
                + " (" + name(format) + ", " + rendered.length() + " симв.)");
        return 0;
    }

    // ========================================================================
    // Рендеринг
    // ========================================================================

    private static String renderText(FictionBookDto book) {
        PlainTextRenderer renderer = new PlainTextRenderer();
        BookPlayer player = new BookPlayer(renderer, href -> resolve(book, href));
        renderFrontMatter(renderer, player, book);
        player.play(book);
        return renderer.getOutput();
    }

    private static String renderHtml(FictionBookDto book, Path output, boolean toStdout,
                                     ImageMode imageMode, boolean wrapDocument) {
        ResourceResolver resolver = switch (imageMode) {
            case EMBED -> ResourceResolver.base64DataUri();
            case NONE -> ResourceResolver.placeholder();
            case EXTRACT -> {
                // При выводе в stdout некуда складывать файлы — встраиваем в base64.
                if (toStdout) {
                    yield ResourceResolver.base64DataUri();
                }
                String base = stripExtension(output.getFileName().toString());
                Path dir = parentOrCurrent(output).resolve(base + "_files");
                yield ResourceResolver.saveToDirectory(dir, base + "_files/");
            }
        };

        HtmlRenderer renderer = HtmlRenderer.builder()
                .resourceResolver(resolver)
                .wrapInHtmlDocument(wrapDocument)
                .title(bookTitle(book))
                .build();

        // href картинок приходит как "#id"; resources хранятся по id без '#'.
        BookPlayer player = new BookPlayer(renderer, href -> resolve(book, href));
        renderFrontMatter(renderer, player, book);
        player.play(book);
        return renderer.getOutput();
    }

    private static String renderMarkdown(FictionBookDto book, Path output, boolean toStdout,
                                         ImageMode imageMode) {
        ResourceResolver resolver = switch (imageMode) {
            case EMBED -> ResourceResolver.base64DataUri();
            // null → картинка станет текстовым placeholder'ом в Markdown.
            case NONE -> null;
            case EXTRACT -> {
                if (toStdout) {
                    yield ResourceResolver.base64DataUri();
                }
                String base = stripExtension(output.getFileName().toString());
                Path dir = parentOrCurrent(output).resolve(base + "_files");
                yield ResourceResolver.saveToDirectory(dir, base + "_files/");
            }
        };

        MarkdownRenderer renderer = new MarkdownRenderer(resolver);
        BookPlayer player = new BookPlayer(renderer, href -> resolve(book, href));
        renderFrontMatter(renderer, player, book);
        player.play(book);
        return renderer.getOutput();
    }

    /**
     * Рендерит «шапку» книги из метаданных {@code <description>} перед телом:
     * название, авторов, обложку и аннотацию. {@link BookPlayer} проигрывает только
     * {@code <body>}, поэтому без этого название/аннотация/обложка (она лежит в
     * {@code <coverpage>}) не попадают в вывод. Всё оборачивается в отдельную секцию,
     * чтобы HTML-рендерер успел открыть документ до первого абзаца.
     */
    private static void renderFrontMatter(FictionBookRenderer renderer, BookPlayer player,
                                          FictionBookDto book) {
        BookInfo info = FictionBookIO.info(book);

        boolean hasTitle = info.title() != null && !info.title().isBlank();
        boolean hasAuthors = info.authorsLine() != null && !info.authorsLine().isBlank();
        boolean hasCover = info.cover() != null;
        boolean hasAnnotation = info.annotation() != null && !info.annotation().isEmpty();
        if (!hasTitle && !hasAuthors && !hasCover && !hasAnnotation) {
            return;
        }

        renderer.startSection(null);

        if (hasTitle) {
            renderer.startParagraph(ParagraphStyle.SECTION_TITLE);
            renderer.text(info.title());
            renderer.endParagraph();
        }
        if (hasAuthors) {
            renderer.startParagraph(ParagraphStyle.TEXT_AUTHOR);
            renderer.text(info.authorsLine());
            renderer.endParagraph();
        }
        if (hasCover) {
            // alt=null: в txt не дублируем название, в HTML картинка всё равно вставится.
            renderer.image(info.cover(), null);
        }
        if (hasAnnotation) {
            for (BlockElement block : info.annotation()) {
                player.playBlock(block);
            }
        }

        renderer.endSection();
    }

    private static Resource resolve(FictionBookDto book, String href) {
        if (href == null) {
            return null;
        }
        String key = href.startsWith("#") ? href.substring(1) : href;
        return book.resources().get(key);
    }

    private static String bookTitle(FictionBookDto book) {
        if (book.description() != null && book.description().titleInfo() != null) {
            return book.description().titleInfo().bookTitle();
        }
        return null;
    }

    // ========================================================================
    // Разбор и производные пути
    // ========================================================================

    private static String next(String[] args, int i, PrintStream err, String opt) {
        if (i >= args.length) {
            err.println("Опция " + opt + " требует значение.");
            return null;
        }
        return args[i];
    }

    private static Target parseTarget(String value, PrintStream err) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "txt", "text" -> Target.TXT;
            case "html", "htm" -> Target.HTML;
            case "md", "markdown" -> Target.MD;
            case "fb2" -> Target.FB2;
            case "fb3" -> Target.FB3;
            default -> {
                err.println("Неизвестный формат: " + value
                        + " (ожидается txt, html, md, fb2 или fb3)");
                yield null;
            }
        };
    }

    private static ImageMode parseImageMode(String value, PrintStream err) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "embed", "base64" -> ImageMode.EMBED;
            case "extract", "save" -> ImageMode.EXTRACT;
            case "none", "skip" -> ImageMode.NONE;
            default -> {
                err.println("Неизвестный режим картинок: " + value
                        + " (ожидается embed, extract или none)");
                yield null;
            }
        };
    }

    private static Target inferTarget(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            return Target.HTML;
        }
        if (name.endsWith(".md") || name.endsWith(".markdown")) {
            return Target.MD;
        }
        if (name.endsWith(".fb2")) {
            return Target.FB2;
        }
        if (name.endsWith(".fb3")) {
            return Target.FB3;
        }
        return Target.TXT;
    }

    private static Path deriveOutput(Path input, Target format) {
        String base = stripExtension(input.getFileName().toString());
        Path target = Path.of(base + extensionOf(format));
        Path parent = input.toAbsolutePath().getParent();
        return parent != null ? parent.resolve(target) : target;
    }

    /** Путь вывода для пакетного режима: в {@code outDir} (если задан), иначе рядом с книгой. */
    private static Path batchOutput(Path book, Path outDir, Target format) {
        String name = stripExtension(book.getFileName().toString()) + extensionOf(format);
        Path dir = outDir != null ? outDir : parentOrCurrent(book);
        return dir.resolve(name);
    }

    /** Делает имя вывода уникальным, вставляя расширение источника: {@code book_fb3.fb3}. */
    private static Path disambiguate(Path dest, Path book, Target format) {
        String srcName = book.getFileName().toString();
        int dot = srcName.lastIndexOf('.');
        String srcExt = dot > 0 ? srcName.substring(dot + 1).toLowerCase(Locale.ROOT) : "src";
        String alt = stripExtension(dest.getFileName().toString()) + "_" + srcExt + extensionOf(format);
        return dest.resolveSibling(alt);
    }

    private static String extensionOf(Target format) {
        return switch (format) {
            case HTML -> ".html";
            case MD -> ".md";
            case TXT -> ".txt";
            case FB2 -> ".fb2";
            case FB3 -> ".fb3";
        };
    }

    private static String name(Target format) {
        return format.name().toLowerCase(Locale.ROOT);
    }

    /** Собирает {@code *.fb2}/{@code *.fb3} из каталога (рекурсивно при {@code recursive}). */
    private static void collectBooks(Path dir, boolean recursive, List<Path> acc) throws IOException {
        try (Stream<Path> stream = recursive ? Files.walk(dir) : Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(FictionBookCli::isBook)
                    .sorted()
                    .forEach(acc::add);
        }
    }

    private static boolean isBook(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".fb2") || n.endsWith(".fb3");
    }

    private static boolean sameFile(Path a, Path b) {
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }

    private static void ensureParent(Path output) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static Path parentOrCurrent(Path path) {
        Path parent = path.toAbsolutePath().getParent();
        return parent != null ? parent : Path.of(".");
    }

    private static void printUsage(PrintStream out) {
        out.println("""
                fictionbook — конвертация книг FB2/FB3 (txt, html, md, fb2, fb3)

                Использование:
                  fb <вход...> [опции]

                Вход — файл(ы) и/или каталог(и). Для каталога берутся все *.fb2/*.fb3.

                Опции:
                  -f, --format <fmt>          формат вывода: txt | html | md | fb2 | fb3
                                              (по умолчанию — по расширению выхода, иначе txt)
                  -o, --output <путь>         файл вывода ('-' — stdout, только txt/html/md).
                                              По умолчанию рядом с входом с новым расширением
                  -d, --out-dir <каталог>     каталог вывода для пакетного режима
                  -r, --recursive             рекурсивный обход входных каталогов
                      --images <режим>        html/md: embed (base64, по умолчанию) |
                                              extract (в папку <имя>_files) | none (без картинок)
                      --no-wrap               html: только фрагмент, без <html>/<head>/<body>
                  -h, --help                  показать эту справку
                  -v, --version               показать версию

                Примеры:
                  fb book.fb2                       # -> book.txt
                  fb book.fb2 -f html
                  fb book.fb3 -f fb2                # FB3 -> FB2
                  fb books/ -f fb3 -d out/ -r       # пакет: каталог -> FB3 в out/
                  fb book.fb2 -o -                  # в stdout""");
    }
}
