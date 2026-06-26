package org.tehlab.whitek0t.fictionbook.cli;

import org.tehlab.whitek0t.fictionbook.api.FictionBookIO;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.dto.Resource;
import org.tehlab.whitek0t.fictionbook.exception.FictionBookException;
import org.tehlab.whitek0t.fictionbook.render.BookPlayer;
import org.tehlab.whitek0t.fictionbook.render.ResourceResolver;
import org.tehlab.whitek0t.fictionbook.render.impl.HtmlRenderer;
import org.tehlab.whitek0t.fictionbook.render.impl.PlainTextRenderer;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Консольная утилита конвертации книг FB2/FB3 в {@code txt} и {@code html}.
 *
 * <p>Это единственная точка входа-приложение в проекте-библиотеке: она лишь
 * связывает публичные фасады ({@link FictionBookIO}, {@link BookPlayer},
 * рендереры) и не добавляет новой логики разбора/рендеринга.</p>
 *
 * <p>Пример:</p>
 * <pre>{@code
 * # FB2 -> текст (по умолчанию рядом с исходником: book.txt)
 * fb book.fb2
 *
 * # FB2 -> HTML с встроенными картинками
 * fb book.fb2 -f html
 *
 * # явный выход и извлечение картинок в отдельную папку
 * fb book.fb2 -o out/book.html --images extract
 *
 * # в stdout (для пайпов)
 * fb book.fb2 -o -
 * }</pre>
 */
public final class FictionBookCli {

    private static final String VERSION = "1.0-SNAPSHOT";

    private FictionBookCli() {
    }

    /** Формат вывода. */
    private enum Target {TXT, HTML}

    /** Режим обработки картинок при выводе в HTML. */
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
     * Выполняет одну конвертацию. Вынесено из {@link #main} ради тестируемости:
     * потоки вывода передаются явно, а результат — код возврата (0 — успех).
     *
     * @param args аргументы командной строки
     * @param out  поток для полезного вывода (например, при {@code -o -})
     * @param err  поток для сообщений и ошибок
     * @return код возврата: 0 — успех, 1 — ошибка чтения/записи, 2 — ошибка аргументов
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        // --- Разбор аргументов -------------------------------------------------
        Path input = null;
        Path output = null;
        Target format = null;
        ImageMode imageMode = ImageMode.EMBED;
        boolean wrapDocument = true;

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
                    if (input == null) {
                        input = Path.of(arg);
                    } else if (output == null) {
                        output = Path.of(arg);
                    } else {
                        err.println("Лишний аргумент: " + arg);
                        return 2;
                    }
                }
            }
        }

        if (input == null) {
            err.println("Не указан входной файл.");
            printUsage(err);
            return 2;
        }
        if (!Files.isRegularFile(input)) {
            err.println("Файл не найден: " + input);
            return 1;
        }

        // --- Определяем формат и путь вывода ----------------------------------
        boolean toStdout = output != null && output.toString().equals("-");

        if (format == null) {
            format = toStdout ? Target.TXT : inferTarget(output != null ? output : input);
        }
        if (output == null) {
            output = deriveOutput(input, format);
        }

        // --- Чтение -----------------------------------------------------------
        FictionBookDto book;
        try {
            book = FictionBookIO.read(input);
        } catch (FictionBookException e) {
            err.println("Ошибка чтения '" + input + "': " + e.getMessage());
            return 1;
        }

        // --- Рендеринг --------------------------------------------------------
        String rendered = switch (format) {
            case TXT -> renderText(book);
            case HTML -> renderHtml(book, output, toStdout, imageMode, wrapDocument);
        };

        // --- Запись -----------------------------------------------------------
        if (toStdout) {
            out.print(rendered);
            if (!rendered.endsWith("\n")) {
                out.println();
            }
            return 0;
        }

        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(output, rendered, StandardCharsets.UTF_8);
        } catch (Exception e) {
            err.println("Ошибка записи '" + output + "': " + e.getMessage());
            return 1;
        }

        err.println("Готово: " + input + " → " + output
                + " (" + format.name().toLowerCase(Locale.ROOT) + ", "
                + rendered.length() + " симв.)");
        return 0;
    }

    // ========================================================================
    // Рендеринг
    // ========================================================================

    private static String renderText(FictionBookDto book) {
        PlainTextRenderer renderer = new PlainTextRenderer();
        new BookPlayer(renderer).play(book);
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
        new BookPlayer(renderer, href -> resolve(book, href)).play(book);
        return renderer.getOutput();
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
            default -> {
                err.println("Неизвестный формат: " + value + " (ожидается txt или html)");
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
        return Target.TXT;
    }

    private static Path deriveOutput(Path input, Target format) {
        String base = stripExtension(input.getFileName().toString());
        String ext = format == Target.HTML ? ".html" : ".txt";
        Path parent = input.toAbsolutePath().getParent();
        Path target = Path.of(base + ext);
        return parent != null ? parent.resolve(target) : target;
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
                fictionbook — конвертация книг FB2/FB3 в txt и html

                Использование:
                  fb <вход> [выход] [опции]

                Опции:
                  -f, --format <txt|html>   формат вывода (по умолчанию — по расширению
                                            выхода, иначе txt)
                  -o, --output <путь>       файл вывода ('-' — stdout). По умолчанию рядом
                                            с входом с новым расширением
                      --images <режим>      html: embed (base64, по умолчанию) |
                                            extract (в папку <имя>_files) | none (без картинок)
                      --no-wrap             html: только фрагмент, без <html>/<head>/<body>
                  -h, --help                показать эту справку
                  -v, --version             показать версию

                Примеры:
                  fb book.fb2
                  fb book.fb2 -f html
                  fb book.fb2 -o out/book.html --images extract
                  fb book.fb2 -o -            # в stdout""");
    }
}
