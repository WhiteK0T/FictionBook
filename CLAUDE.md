# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A Java 21 library for reading, writing, sanitizing, and rendering FictionBook e-book files (FB2 today; FB3 planned). The core idea: parse any (often "dirty") FB2 into one immutable domain model (`FictionBookDto`), then render or write it back out in a strictly-valid form. This is primarily a library consumed via the `api/` facades. There is one thin entry-point application — `cli/FictionBookCli`, a FB2/FB3 → txt/html converter that only wires the public facades together; the Gradle `application` plugin exposes it via `./gradlew run` / `installDist` (launcher name `fb`).

Note: most of the design docs in the repo root are in Russian (`Архитектура проекта.md` = architecture, `Примеры использования.md` = usage cookbook, `STATUS.md` = full status/ADR log, `Changelog.md`). `STATUS.md` is the authoritative living status document — check it for what is done vs. planned before assuming a feature exists.

## Commands

Build, test, and run all use the Gradle wrapper:

```bash
./gradlew build                 # compile + test + jars (sources + javadoc)
./gradlew test                  # run all tests
./gradlew test --tests "org.tehlab.whitek0t.fictionbook.internal.writer.fb2.Fb2WriterTest"   # single test class
./gradlew test --tests "*Fb2BlockParserTest"           # by simple class name pattern
./gradlew test --tests "*Fb2WriterTest.someMethod"     # single method
./gradlew compileJava            # compile main only
./gradlew jmh                    # run JMH benchmarks (src/jmh/java)
./gradlew run --args="book.fb2 -f html"   # run the CLI converter (cli/FictionBookCli)
./gradlew installDist            # build runnable distribution → build/install/fb/bin/fb
```

Lombok is applied via the `io.freefair.lombok` plugin (annotation processing). There is no separate lint task. `maven-publish` is configured but the publication block is commented out.

## Architecture

The flow is: **bytes → encoding detection → hybrid parse → `FictionBookDto` → (sanitize) → write / render.**

### Public API (`api/`)
`FictionBookIO` is the single entry point: `read(Path)` / `write(dto, Path)`. Format is auto-detected by `FictionBookFormat.detect()` (magic bytes first — ZIP=FB3, XML=FB2 — then extension fallback). Both facades dispatch on a `switch` over the format enum; FB3 branches currently throw `UnsupportedOperationException`.

### Domain model (`dto/`) — immutable Java Records
Everything is records, so transforming the tree means rebuilding it, not mutating it. Three layers:
- `dto/description/` — metadata (`Description`, `TitleInfo`, `Author`, `PublishInfo`, …)
- `dto/block/` — block elements (`Section`, `Paragraph`, `Poem`, `Table`, `Cite`, `Epigraph`)
- `dto/inline/` — inline/mixed content (`Text`, `Strong`, `Emphasis`, `Link`, `ImageRef`, …)

The DTO holds **no indexes**. Anchor/cross-reference lookups are built separately and on demand via `AnchorIndexBuilder.fromDto()` → `AnchorIndex` (`internal/anchor/`).

### Reading is hybrid (`internal/parser/`, `internal/reader/`)
A single pass over the FB2 XML uses two parsers for different reasons:
- **Jackson XML** (`parser/jackson/`, the `*Jax` classes + `DescriptionMapper`) for the structured `<description>` block.
- **StAX/Woodstox** (`parser/stax/`) for `<body>`, which is mixed content. This uses a **stack of `NodeBuilder`s** (one per element kind: `ParagraphBuilder`, `InlineContainerBuilder`, `LinkBuilder`, `ImageBuilder`, `VerseBuilder`, `TableCellBuilder`, `IgnoreBuilder`). `Fb2BodyParser` walks sections recursively; `Fb2BlockParser` builds individual blocks.

Binaries (base64 images) are loaded **eagerly** during read (`Fb2Reader`) — there is no lazy/seek path. Reading is intentionally forgiving of malformed input.

### Writing is strict (`internal/writer/`)
`Fb2Writer` uses `XMLStreamWriter`, streams base64 in chunks (does not buffer whole images), and pretty-prints. It **auto-runs the sanitizer pipeline before writing** so output is standard-compliant even when input was not.

### Sanitizers (`internal/sanitizer/`) — Chain of Responsibility
`SanitizerPipeline.standard()` defines the canonical order; **order matters** (e.g. `EmptyParagraphCleaner` → `EmptySectionCleaner` → `TextNodeMerger` → orphan cleaners → `AttributeNormalizer`). Each `Sanitizer` takes a DTO and returns a new DTO. The pipeline is fault-tolerant: a sanitizer returning `null` or throwing is logged and skipped, keeping the previous state. `FictionBookDtoTransformer` is the recursive-rebuild helper sanitizers use to walk the immutable tree.

### Rendering (`render/`) — Command Pattern / event-driven
The book "plays itself" onto a renderer: `BookPlayer` traverses the DTO and emits callbacks to a `FictionBookRenderer`, maintaining a context stack of `ParagraphStyle`. Implementations live in `render/impl/` (`HtmlRenderer`, `PlainTextRenderer`). Images are resolved through the `ResourceResolver` abstraction (placeholder / base64 data-URI / save-to-directory), with `MimeTypeResolver` mapping MIME → file extension. To add an output format, implement `FictionBookRenderer` rather than touching `BookPlayer`.

### Encoding (`encoding/`)
`EncodingDetector` resolves charset by BOM → XML declaration → Mozilla `juniversalchardet`. `EncodingAwareInputStream` / `ByteCountingInputStream` (`internal/io/`) wrap the raw stream.

## Conventions

- **Java 21**, immutable records throughout, sealed-style element hierarchies (`BlockElement`, `InlineElement`). Prefer rebuilding DTOs via transformer helpers over introducing mutability.
- **`internal/` is private implementation** — keep new public surface in `api/`, `dto/`, and `render/`.
- **Logging** uses SLF4J API only (no bound implementation shipped); tests use `slf4j-simple`.
- **Tests**: JUnit 5 + AssertJ. Tests are organized with nested `@Nested` classes by feature. **jqwik** powers the property-based and fuzz tests (`Fb2RoundTripPropertyTest`, `Fb2ReaderFuzzTest`); the `.jqwik-database` file is jqwik's failure-replay store. **JMH** benchmarks live in `src/jmh/java` (`me.champeau.jmh` plugin) and run via `./gradlew jmh`.
- Errors surface as `FictionBookException` / `InvalidFormatException` (use the factory methods on the exception types).
