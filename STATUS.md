
# Контекст проекта

Разрабатывается Java-библиотека для работы с форматами электронных книг FB2 и FB3.

**Цели:**
- Чтение и запись FB2/FB3 файлов
- Конвертация между форматами
- Рендеринг в HTML, PlainText и другие форматы
- Пакетная обработка библиотек
- Использование в читалках и веб-сервисах

**Целевая аудитория:**
- Разработчики читалок
- Сервисы конвертации документов
- Системы каталогизации библиотек

---

# Архитектурные решения (ADR)

## ADR-001: Unified Domain Model
**Решение:** Единая модель `FictionBookDto` (Java Records) для обоих форматов.
**Обоснование:** Пользователь работает с абстракцией "Книга", не заботясь о формате.

## ADR-002: Immutable DTO
**Решение:** Все DTO построены на Java Records.
**Обоснование:** Потокобезопасность, предсказуемость, легкая сериализация.

## ADR-003: Прощающее чтение, строгая запись
**Решение:** При чтении допускаются отклонения от стандарта, при записи — строгое соответствие через санитайзеры.
**Обоснование:** Реальные FB2-файлы часто "грязные", но выход должен быть валидным.

## ADR-004: Гибридный парсинг
**Решение:** Jackson XML для `<description>`, StAX для `<body>`.
**Обоснование:** Jackson удобен для структурированных данных, StAX — для mixed content и больших файлов.

## ADR-005: Eager-загрузка бинарников
**Решение:** Base64-картинки декодируются сразу при чтении.
**Обоснование:** Lazy-чтение через seek требует сложных offset'ов и не работает с перекодированными потоками. Бинарники обычно небольшие (50-500 KB).

## ADR-006: Event-driven рендеринг
**Решение:** Command Pattern — книга "проигрывает" себя на рендерер через `BookPlayer`.
**Обоснование:** Инкапсуляция логики обхода дерева, простые рендереры.

## ADR-007: DTO не содержит индексов
**Решение:** `AnchorIndex` строится отдельно через `AnchorIndexBuilder.fromDto()`.
**Обоснование:** DTO — чистые данные. Индекс нужен не всегда, его построение имеет стоимость.

## ADR-008: Санитизация перед записью
**Решение:** `SanitizerPipeline` — цепочка санитайзеров (Chain of Responsibility).
**Обоснование:** Приведение к стандарту, удаление мусора, валидация ссылок.

---

# Стек технологий

| Компонент | Выбор | Версия |
|---|---|---|
| Java | JDK | 21+ |
| XML (мета) | Jackson XML | 2.17.1 |
| XML (тело) | StAX (Woodstox) | Встроен в Jackson |
| ZIP | java.util.zip | JDK |
| Кодировки | juniversalchardet | 1.0.3 |
| Логирование | SLF4J API | 2.0.13 |
| Тесты | JUnit 5 + AssertJ + jqwik | 5.10.2 / 3.25.3 / 1.8.4 |
| Сборка | Gradle (Kotlin DSL) | 8.x |

---

# Структура проекта

```
org.tehlab.whitek0t.fictionbook/
├── api/                    # Публичные фасады (FictionBookIO, FictionBookFormat)
├── dto/                    # Immutable Records (FictionBookDto, BodyDto, etc.)
│   ├── description/        # Метаданные
│   ├── block/              # Блочные элементы (Section, Paragraph, Poem, Table)
│   └── inline/             # Инлайн-элементы (Text, Strong, Link, ImageRef)
├── render/                 # Event-driven рендеринг
│   ├── FictionBookRenderer # Интерфейс рендерера
│   ├── BookPlayer          # "Проигрыватель" книги
│   ├── ParagraphStyle      # Enum стилей
│   ├── ResourceResolver    # Абстракция для картинок
│   └── impl/               # HtmlRenderer, PlainTextRenderer
├── internal/               # Внутренняя реализация
│   ├── parser/jackson/     # Jax-классы + DescriptionMapper
│   ├── parser/stax/        # NodeBuilder'ы, Fb2BlockParser, Fb2BodyParser
│   ├── reader/fb2/         # Fb2Reader (eager base64)
│   ├── writer/fb2/         # Fb2Writer (streaming base64)
│   ├── sanitizer/          # 6 санитайзеров + Pipeline + Transformer
│   ├── anchor/             # AnchorIndex + AnchorIndexBuilder
│   └── io/                 # ByteCountingInputStream
├── encoding/               # EncodingDetector, EncodingAwareInputStream
├── exception/              # FictionBookException, InvalidFormatException
└── util/                   # MimeTypeResolver
```

---

# Текущий статус реализации

## ✅ Полностью реализовано

### Core
- [x] DTO модель — все immutable Records (3 слоя: `description/`, `block/`, `inline/`)
- [x] FictionBookIO — единая точка входа (`read`/`write`, switch по формату;
      FB3-ветки бросают `UnsupportedOperationException`)
- [x] FictionBookFormat — `detect()` по magic bytes (ZIP→FB3, XML→FB2) с fallback
      на расширение (`fromPath`)
- [x] FictionBookException + InvalidFormatException с богатым набором фабричных
      методов (`missingElement`, `missingAttribute`, `invalidAttributeValue`,
      `brokenReference`, `brokenArchive`, `missingFb3Entry`, `validationError`, …)
- [x] EncodingDetector (`detect(Path)`: BOM → XML declaration → Mozilla
      juniversalchardet → fallback UTF-8; BOM-ы UTF-8 / UTF-16LE / UTF-16BE).
      Покрыто `EncodingDetectorTest` (11 тестов: BOM, декларация, приоритеты, fallback)
- [x] EncodingAwareInputStream (читает в исходной кодировке, отдаёт UTF-8, обрезает BOM).
      Покрыто `EncodingAwareInputStreamTest` (5 тестов). Тесты вскрыли, что BOM на
      самом деле НЕ обрезался (UTF-кодеки отдают его как `U+FEFF`) — починено.
- [x] ByteCountingInputStream (трекинг byte offset для AnchorIndex)

### FB2 Reader
- [x] Fb2Reader (один проход, гибридный Jackson+StAX), прощающий режим —
      неизвестные теги пропускаются, не ломая разбор
- [x] Jackson-маппинг для `<description>` (Jax-классы + DescriptionMapper): авторы,
      жанры, sequence, coverpage, title-info, document-info, publish-info
- [x] Mixed content `<annotation>` / `<history>` (раньше терялись при чтении —
      `@JacksonXmlText` не захватывает вложенные `<p>`). Чинит `MixedContentCapture`:
      сырой внутренний XML вытаскивается из поддерева `<description>` и подаётся в
      штатный `parseXmlFragment`. Покрыто round-trip фикспоинт-тестом (write→read→write).
- [x] StAX-парсинг для `<body>` (стек билдеров)
- [x] Fb2BlockParser — все блочные элементы: `<p>`, `<empty-line>`, `<subtitle>`,
      `<poem>` (со строфами `<stanza>`/`<v>`, title, epigraph, text-author),
      `<table>` (со строками `<tr>` и ячейками `<td>`/`<th>`, авто-обёртка «грязного»
      текста через `TableCellBuilder`), `<cite>` и `<epigraph>` (с `id` и `text-author`).
      Покрыто `Fb2BlockParserTest` (отдельный `@Nested` на каждый элемент).
- [x] NodeBuilder'ы: Paragraph, Inline, Link, Image, TableCell, Verse, IgnoreBuilder
- [x] Fb2BodyParser (рекурсивный обход секций)
- [x] Eager-загрузка бинарников (base64 decode)
- [x] Покрыто `Fb2ReaderTest` (27 тестов: `@Nested` Basic / Description / Body,
      несколько `<body>` main+notes), `AuthorTest`, `NodeBuildersTest`

### FB2 Writer
- [x] Fb2Writer (StAX XMLStreamWriter)
- [x] Полный охват записи: `<description>` (title/document/publish-info, авторы),
      все блоки (`p`, `subtitle`, `empty-line`, `poem`/`stanza`/`v`, `table`/`tr`/`td`,
      `cite`, `epigraph`) и инлайны (strong/emphasis/strikethrough/sub/sup, `a`, `image`)
- [x] UTF-8: явная кодировка в XML declaration и при записи
- [x] xlink namespace для ссылок/картинок (`l:href` через `XLINK_NS`)
- [x] Стриминг base64 чанками по 3 КБ (кратно 3 — чистый base64, не грузит картинку в RAM)
- [x] Pretty print (настраивается через `setPrettyPrint`)
- [x] Авто-санитизация перед записью (пайплайн настраивается через `setSanitizerPipeline`)
- [x] Покрыто `Fb2WriterTest` (19 тестов) + round-trip фикспоинтом (`Fb2RoundTripTest`)

### Санитайзеры
- [x] Sanitizer интерфейс
- [x] SanitizerPipeline (Chain of Responsibility)
- [x] FictionBookDtoTransformer (рекурсивный обход immutable DTO; контейнерные
      инлайны, включая `Link`, тоже проходят через `onInlineElement` — раньше колбэк
      видел только листья, из-за чего `OrphanedLinkCleaner` был no-op)
- [x] EmptyParagraphCleaner
- [x] EmptySectionCleaner
- [x] TextNodeMerger (склейка разбитых Text-нод, в т.ч. рекурсивно внутри
      вложенных инлайнов — иначе экранирование `>` нестабильно при round-trip)
- [x] OrphanedImageCleaner (проверка ссылок на картинки)
- [x] OrphanedLinkCleaner (проверка внутренних ссылок)
- [x] AttributeNormalizer (NCName compliance для id)
- [x] `SanitizerPipeline.standard()` — канонический порядок (важен!):
      EmptyParagraphCleaner → EmptySectionCleaner → TextNodeMerger →
      OrphanedImageCleaner → OrphanedLinkCleaner → AttributeNormalizer.
- [x] Отказоустойчивость пайплайна: санитайзер, вернувший `null` или бросивший
      исключение, логируется и пропускается — сохраняется предыдущее состояние DTO.
- [x] Авто-запуск пайплайна в `Fb2Writer` перед записью (forgiving read → strict write).
- [x] Покрыто `SanitizersTest` (15 тестов, включая проверку идемпотентности,
      сборки через `SanitizerPipeline` и все ветки `OrphanedLinkCleaner`:
      резолвящаяся/битая/внешняя ссылка, маркировка битой без текста).

### AnchorIndex
- [x] AnchorIndex (immutable, на базе `Map<String, AnchorInfo>`):
      `find(id)`, `resolve(href)` (срезает `#`), `contains`, `canResolve`, `empty()`
- [x] AnchorInfo (record: id, elementType, byteOffset, lineNumber, bodyName, domNode;
      хелперы `hasByteOffset()` / `hasDomNode()`)
- [x] AnchorIndexBuilder.fromDto() — индексирует id у section, cite, epigraph, poem
      и бинарников; bodyName проставляется при обходе тел. Прощающий режим: при
      коллизии id остаётся первый зарегистрированный.
- [x] Покрыто `AnchorIndexTest` (21 тест: AnchorInfo, поиск/резолв, fromDto). Тесты
      вскрыли NPE в `canResolve`/`contains` на внешней ссылке (`null` id → запрос
      к immutable-мапе) — починено null-проверкой в `contains`.

### Рендеринг
- [x] FictionBookRenderer интерфейс (Command Pattern)
- [x] BookPlayer (с контекстным стеком для ParagraphStyle)
- [x] ParagraphStyle (13 стилей: NORMAL, SECTION_TITLE, CITATION, VERSE, etc.)
- [x] ResourceResolver (placeholder, base64DataUri, saveToDirectory)
- [x] HtmlRenderer (полный HTML5 с CSS, builder API, экранирование, внешние
      ссылки в новой вкладке, режимы wrap-in-document / фрагмент)
- [x] PlainTextRenderer (подсчёт слов, превью, статистика, опциональный alt картинок)
- [x] MimeTypeResolver (MIME → file extension; лежит в `util/`, не в `render/`)
- [x] Покрыто `RenderersTest` (13 тестов: экранирование, форматирование, ссылки,
      ParagraphStyle, оба рендерера, все режимы `ResourceResolver`)

### Тестирование
- [x] Round-trip фикспоинт-тесты (`Fb2RoundTripTest`: write→read→write байт-в-байт +
      сохранность метаданных, тела, annotation/history, бинарников)
- [x] Property-based round-trip (`Fb2RoundTripPropertyTest`, jqwik): фикспоинт на
      случайных DTO-деревьях; вскрыл баги порядка `<binary>` и склейки вложенного текста
- [x] Fuzz-тесты прощающего чтения (`Fb2ReaderFuzzTest`, jqwik): на случайных байтах и
      покалеченном FB2 ридер обязан вернуть непустой DTO либо бросить `FictionBookException`
      — никаких `NPE`/`XMLStreamException`/`StackOverflowError` наружу
- [x] JMH-бенчмарки (`Fb2Benchmark` в `src/jmh/java`, плагин `me.champeau.jmh`):
      read / write / sanitize / round-trip на книгах 10 и 100 секций; запуск `./gradlew jmh`
- [x] Юнит-тесты по компонентам (см. пометки «Покрыто …» в разделах выше)

## ⚠️ Частично реализовано

- [ ] `<poem>`/`<date>` — дата стихотворения вычитывается, но в DTO не сохраняется
      (`Poem` не хранит поле date).
- [ ] `text-author` в poem/cite/epigraph хранится как plain `String` —
      форматирование внутри автора схлопывается в текст.
- [ ] `EncodingAwareInputStream` декодирует исходную кодировку и тут же
      перекодирует в UTF-8 (двойная транскодировка) — кандидат на оптимизацию.
- [ ] AnchorIndex: при `fromDto()` `byteOffset` и `lineNumber` = −1 (заполняются
      только в будущем streaming-режиме); `domNode` всегда `null`.
- [ ] AnchorIndex: paragraph-уровневые `id` не индексируются (только section,
      cite, epigraph, poem, бинарники).

## ⏳ В планах (не начато)

### FB3 поддержка
- [ ] Fb3Reader — распаковка ZIP, парсинг `relations.xml`, склейка `body.xml` + `notes.xml`
- [ ] Fb3Writer — `Fb3ExportContext` с UUID-маппингом, генерация `relations.xml`, `[Content_Types].xml`, `core.xml`

### Streaming API
- [ ] FictionBookStreamer — интерфейс в `api/` уже есть, но `open()` возвращает
      заглушку: все методы (`readDescription`, `readNextSection`, `getResource`,
      `buildAnchorIndex`) отдают `null`. Реальная реализация — TODO.
- [ ] AnchorIndex с byte offset для seek в Streaming режиме
- [ ] Lazy-загрузка бинарников через RandomAccessFile (опционально)

### Дополнительные рендереры
- [ ] JavaFxRenderer — для настольных читалок
- [ ] PdfRenderer — через iText или Apache PDFBox
- [ ] EpubRenderer — генерация EPUB из FB2
- [ ] MarkdownRenderer — конвертация в Markdown

### Улучшения
- [ ] Mutable Model — для удобного редактирования (вместо пересоздания immutable DTO)
- [ ] CSS поддержка в FB3 (задел: `metadata` в `Section`)

### Инфраструктура
- [ ] CLI-утилита — конвертер fb2↔fb3↔html
- [ ] Каталогизатор — пакетная обработка библиотек
- [ ] Интеграция с Elasticsearch — через PlainTextRenderer
- [ ] CI/CD (GitHub Actions)
- [ ] Maven Central публикация

---

# Ключевые классы и их назначение

## Публичный API

### FictionBookIO
Фасад для чтения/записи с auto-detect формата.
```java
FictionBookDto book = FictionBookIO.read(Path.of("book.fb2"));
FictionBookIO.write(book, Path.of("output.fb2"));
```

### FictionBookFormat
Enum с методами `detect(Path)` и `fromPath(Path)`.

## DTO (Immutable Records)

### FictionBookDto
Корневой DTO: `description`, `bodies`, `resources`.

### Resource
Ресурс (картинка) с ленивым `ResourceDataProvider`:
```java
public record Resource(String id, String contentType, ResourceDataProvider dataProvider) {}
```

## Рендеринг

### BookPlayer
"Проигрывает" книгу на рендерер, управляя контекстом (стек стилей):
```java
BookPlayer player = new BookPlayer(renderer, resourceResolver);
player.play(book);
```

### HtmlRenderer
Builder API, поддержка CSS, ResourceResolver:
```java
HtmlRenderer renderer = HtmlRenderer.builder()
    .wrapInHtmlDocument(true)
    .resourceResolver(ResourceResolver.base64DataUri())
    .build();
```

### PlainTextRenderer
Статистика, превью:
```java
int words = renderer.getWordCount();
String preview = renderer.getPreview(500);
```

## Санитайзеры

### SanitizerPipeline
Цепочка санитайзеров:
```java
SanitizerPipeline pipeline = SanitizerPipeline.standard();
FictionBookDto clean = pipeline.sanitize(book);
```

### FictionBookDtoTransformer
Рекурсивный обход immutable DTO:
```java
FictionBookDtoTransformer.transform(book)
    .onParagraph(p -> /* трансформация */)
    .apply();
```

---

# Примеры использования

## Чтение книги
```java
FictionBookDto book = FictionBookIO.read(Path.of("book.fb2"));
String title = book.description().titleInfo().bookTitle();
List<Author> authors = book.description().titleInfo().authors();
```

## Запись книги
```java
FictionBookIO.write(book, Path.of("output.fb2")); // Авто-санитизация
```

## Рендеринг в HTML
```java
HtmlRenderer renderer = HtmlRenderer.builder()
    .wrapInHtmlDocument(true)
    .title(book.description().titleInfo().bookTitle())
    .resourceResolver(ResourceResolver.saveToDirectory(imagesDir, "images/"))
    .build();

BookPlayer player = new BookPlayer(renderer, href -> resolveResource(book, href));
player.play(book);

Files.writeString(Path.of("book.html"), renderer.getOutput());
```

## Извлечение plain text
```java
PlainTextRenderer renderer = new PlainTextRenderer();
new BookPlayer(renderer).play(book);

String text = renderer.getOutput();
int words = renderer.getWordCount();
String preview = renderer.getPreview(500);
```

## Работа с санитайзерами
```java
SanitizerPipeline custom = SanitizerPipeline.builder()
    .add(new EmptyParagraphCleaner())
    .add(new TextNodeMerger())
    .add(new MyCustomSanitizer())
    .build();

FictionBookDto clean = custom.sanitize(book);
```

---

# Следующие шаги (приоритеты)

## Высокий приоритет
1. **Fb3Reader** — базовая поддержка FB3 (ZIP + XML)
2. **Fb3Writer** — генерация FB3 с UUID-маппингом

## Средний приоритет
3. **FictionBookStreamer** — Streaming API для читалок
4. **Mutable Model** — для удобного редактирования
5. **JavaFxRenderer** — для настольных читалок

## Низкий приоритет
6. **PDF/EPUB рендереры**
7. **CLI-утилита**
8. **CSS в FB3**

---

# Приложения

## Приложение A: Архитектура проекта
Полная документация по архитектуре — см. файл `Архитектура проекта.md`

## Приложение B: Примеры использования
Готовые рецепты — см. файл `Примеры использования.md`

## Приложение C: Спецификация FB2
Официальная спецификация: http://www.gribuser.ru/xml/fictionbook/2.0

## Приложение D: Спецификация FB3
Черновик спецификации: http://fictionbook.org (статус: не утверждён, последние обновления ~2015-2018)

---

# Инструкции для новой сессии

1. **Изучи контекст:** прочитай этот промт и приложения
2. **Проверь статус:** посмотри раздел "Текущий статус реализации"
3. **Выбери задачу:** начни с высокого приоритета или того, что интересно
4. **Следуй архитектуре:** соблюдай принятые ADR
5. **Пиши тесты:** для каждой новой фичи добавляй unit-тесты
6. **Обновляй документацию:** при изменении архитектуры обновляй `Архитектура проекта.md`

---

# Вопросы для уточнения

Если что-то непонятно или нужно больше деталей:
- Спроси про конкретный ADR
- Запроси примеры кода для конкретного компонента
- Уточни требования к новой фиче

---


