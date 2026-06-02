# Санитайзеры — документация

## Содержание

1. [Введение](#введение)
2. [Архитектура](#архитектура)
3. [Список санитайзеров](#список-санитайзеров)
4. [Примеры использования](#примеры-использования)
5. [Интеграция с Fb2Writer](#интеграция-с-fb2writer)
6. [Лучшие практики](#лучшие-практики)
7. [Создание собственных санитайзеров](#создание-собственных-санитайзеров)

---

## Введение

**Санитайзеры** — это компоненты библиотеки для нормализации и очистки данных книги перед записью в файл FB2/FB3.

### Зачем нужны санитайзеры?

FB2-файлы из интернета часто содержат:

- Пустые параграфы и секции
- Разбитые текстовые ноды (`<p>Текст </p><p>продолжение</p>`)
- Битые ссылки на несуществующие ресурсы
- Невалидные атрибуты (пробелы в `id`, недопустимые символы)
- Дублирующиеся элементы

### Принцип работы

Библиотека следует принципу **"прощающее чтение, строгая запись"**:

- **При чтении** допускаются любые отклонения от стандарта
- **При записи** книга приводится к строгому соответствию спецификации FB2

### Пример проблемы и решения

**До санитизации:**

```xml

<section id=" 123 invalid! ">
    <p></p>
    <p>Текст</p>
    <p>продолжение</p>
    <p>
        <image l:href="#missing_cover"/>
    </p>
</section>
```

**После санитизации:**

```xml

<section id="_23_invalid_">
    <p>Текст продолжение</p>
    <p>[Image Missing: #missing_cover]</p>
</section>
```

---

## Архитектура

### Immutable DTO

Все DTO построены на Java Records — они **неизменяемы**. Любой санитайзер возвращает **новую копию** книги с
изменениями, не модифицируя исходную.

```java
FictionBookDto original = FictionBookIO.read(file);
FictionBookDto cleaned = sanitizer.sanitize(original);

// original остаётся без изменений!
// cleaned — новая копия с применёнными изменениями
```

### SanitizerPipeline

Санитайзеры объединяются в **пайплайн** (цепочку) и применяются последовательно:

```java
SanitizerPipeline pipeline = SanitizerPipeline.builder()
        .add(new EmptyParagraphCleaner())
        .add(new TextNodeMerger())
        .add(new OrphanedImageCleaner())
        .build();

FictionBookDto clean = pipeline.sanitize(book);
```

### Порядок применения важен!

Рекомендуемый порядок:

1. **Очистка пустых узлов** (EmptyParagraphCleaner, EmptySectionCleaner)
2. **Нормализация текста** (TextNodeMerger)
3. **Проверка ссылок** (OrphanedImageCleaner, OrphanedLinkCleaner)
4. **Нормализация атрибутов** (AttributeNormalizer)

---

## Список санитайзеров

### 1. EmptyParagraphCleaner

**Назначение:** Удаляет пустые параграфы.

**Критерии пустого параграфа:**

- Список элементов пуст
- Содержит только `Text`-ноды с пустыми или пробельными строками

**Пример:**

```xml
<!-- До -->
<p></p>
<p>   </p>
<p>
<image l:href="#img"/>
</p>

        <!-- После -->
<p>
<image l:href="#img"/>
</p>
```

**Когда использовать:** Всегда, для удаления мусора.

---

### 2. EmptySectionCleaner

**Назначение:** Удаляет пустые секции (главы).

**Критерии пустой секции:**

- Нет заголовка (или он пустой)
- Нет содержимого (content)
- Нет вложенных секций

**Пример:**

```xml
<!-- До -->
<body>
    <section id="ch1">
        <title>
            <p>Глава 1</p>
        </title>
        <p>Текст главы</p>
    </section>
    <section id="ch2">
        <!-- Пустая секция -->
    </section>
</body>

        <!-- После -->
<body>
<section id="ch1">
    <title>
        <p>Глава 1</p>
    </title>
    <p>Текст главы</p>
</section>
</body>
```

**Когда использовать:** Всегда, для удаления артефактов конвертации.

---

### 3. TextNodeMerger

**Назначение:** Склейвает соседние `Text`-ноды в одну.

**Проблема:** StAX-парсер часто разбивает текст на куски (из-за CDATA, лимитов буфера).

**Пример:**

```xml
<!-- До (разбитые Text-ноды) -->
<p>
    <Text>Текст</Text>
    <Text>продолжение</Text>
</p>

        <!-- После (склеенные) -->
<p>
<Text>Текст продолжение</Text>
</p>
```

**Когда использовать:** Всегда, для приведения к каноническому виду.

---

### 4. OrphanedImageCleaner

**Назначение:** Обрабатывает ссылки на несуществующие ресурсы (картинки).

**Поведение:**

- Если `<image l:href="#cover"/>` ссылается на существующий бинарник — оставляет как есть
- Если бинарник отсутствует — заменяет на текстовую метку `[Image Missing: #cover]`

**Пример:**

```xml
<!-- До (битая ссылка) -->
<p>
    <image l:href="#missing_cover"/>
</p>

        <!-- После -->
<p>[Image Missing: #missing_cover]</p>
```

**Когда использовать:** Всегда, для предотвращения битых ссылок в выходном файле.

---

### 5. OrphanedLinkCleaner

**Назначение:** Проверяет внутренние ссылки (`#anchor`) и помечает битые.

**Поведение:**

- Строит `AnchorIndex` из книги
- Проверяет каждую внутреннюю ссылку
- Если цель не найдена — добавляет префикс `[broken: ...]` к тексту ссылки

**Пример:**

```xml
<!-- До (битая ссылка) -->
<a l:href="#note99">[99]</a>

        <!-- После -->
<a l:href="#note99">[broken: #note99]</a>
```

**Когда использовать:** Всегда, для валидации внутренних ссылок.

---

### 6. AttributeNormalizer

**Назначение:** Нормализует атрибуты узлов (в первую очередь `id`).

**Преобразования:**

- Пустые строки → `null`
- Пробелы обрезаются
- Недопустимые символы заменяются на `_`
- ID должен начинаться с буквы или `_`

**Пример:**

```xml
<!-- До -->
<section id=" 123 invalid id! ">

    <!-- После -->
    <section id="_23_invalid_id_">
```

**Когда использовать:** Всегда, для гарантии валидности XML-атрибутов.

---

## Примеры использования

### Базовое использование

```java
import org.tehlab.whitek0t.fictionbook.api.FictionBookIO;
import org.tehlab.whitek0t.fictionbook.dto.FictionBookDto;
import org.tehlab.whitek0t.fictionbook.internal.sanitizer.*;

public class SanitizerExample {
    public static void main(String[] args) throws Exception {
        // Читаем книгу
        FictionBookDto book = FictionBookIO.read(Path.of("dirty_book.fb2"));

        // Создаём стандартный пайплайн
        SanitizerPipeline pipeline = SanitizerPipeline.standard();

        // Применяем санитайзеры
        FictionBookDto clean = pipeline.sanitize(book);

        // Записываем очищенную книгу
        FictionBookIO.write(clean, Path.of("clean_book.fb2"));
    }
}
```

### Кастомный пайплайн

```java
SanitizerPipeline customPipeline = SanitizerPipeline.builder()
        .add(new EmptyParagraphCleaner())
        .add(new TextNodeMerger())
        // Пропускаем OrphanedImageCleaner — хотим сохранить битые ссылки
        .add(new AttributeNormalizer())
        .build();

FictionBookDto clean = customPipeline.sanitize(book);
```

### Применение одного санитайзера

```java
// Только склейка текстовых нод
FictionBookDto merged = new TextNodeMerger().sanitize(book);

// Только удаление пустых параграфов
FictionBookDto cleaned = new EmptyParagraphCleaner().sanitize(book);
```

### Проверка результата

```java
FictionBookDto before = FictionBookIO.read(Path.of("book.fb2"));
FictionBookDto after = SanitizerPipeline.standard().sanitize(before);

// Сравниваем количество параграфов
long paragraphsBefore = countParagraphs(before);
long paragraphsAfter = countParagraphs(after);

System.out.

println("Удалено пустых параграфов: "+(paragraphsBefore -paragraphsAfter));

private long countParagraphs(FictionBookDto book) {
    return book.bodies().stream()
            .flatMap(body -> body.sections().stream())
            .flatMap(section -> section.content().stream())
            .filter(block -> block instanceof Paragraph)
            .count();
}
```

---

## Интеграция с Fb2Writer

### Автоматическая санитизация

`Fb2Writer` **автоматически** применяет стандартный пайплайн перед записью:

```java
// Санитайзеры применяются автоматически!
FictionBookIO.write(book, Path.of("output.fb2"));
```

### Отключение санитизации

Если нужно записать книгу "как есть" (например, для отладки):

```java
Fb2Writer writer = new Fb2Writer();
writer.

setSanitizerPipeline(null); // Отключаем санитайзеры
writer.

write(book, Path.of("raw_output.fb2"));
```

### Кастомный пайплайн для writer

```java
Fb2Writer writer = new Fb2Writer();
writer.

setSanitizerPipeline(SanitizerPipeline.builder()
    .

add(new TextNodeMerger())
        // Только склейка текстов, без удаления пустых параграфов
        .

build());

        writer.

write(book, Path.of("custom_output.fb2"));
```

---

## Лучшие практики

### 1. Всегда используйте стандартный пайплайн

```java
// ✅ Хорошо
SanitizerPipeline pipeline = SanitizerPipeline.standard();

// ❌ Плохо (легко забыть важный санитайзер)
SanitizerPipeline pipeline = SanitizerPipeline.builder()
        .add(new EmptyParagraphCleaner())
        .add(new TextNodeMerger())
        .build();
```

### 2. Проверяйте результат санитизации

```java
FictionBookDto clean = pipeline.sanitize(book);

// Проверяем, что книга не стала пустой
if(clean.

bodies().

isEmpty()){
        log.

warn("Book became empty after sanitization!");
}

// Проверяем, что все секции имеют содержимое
        clean.

bodies().

forEach(body ->{
        body.

sections().

forEach(section ->{
        if(section.

content().

isEmpty() &&section.

subSections().

isEmpty()){
        log.

warn("Empty section found: {}",section.id());
        }
        });
        });
```

### 3. Логируйте изменения

```java
public class LoggingSanitizer implements Sanitizer {
    private final Sanitizer delegate;

    public LoggingSanitizer(Sanitizer delegate) {
        this.delegate = delegate;
    }

    @Override
    public FictionBookDto sanitize(FictionBookDto book) {
        FictionBookDto result = delegate.sanitize(book);

        // Сравниваем до и после
        int paragraphsBefore = countParagraphs(book);
        int paragraphsAfter = countParagraphs(result);

        if (paragraphsBefore != paragraphsAfter) {
            log.info("{} removed {} paragraphs",
                    delegate.getClass().getSimpleName(),
                    paragraphsBefore - paragraphsAfter);
        }

        return result;
    }
}
```

### 4. Идемпотентность

Санитайзеры должны быть **идемпотентными** — повторный запуск не должен вносить изменений:

```java
FictionBookDto once = pipeline.sanitize(book);
FictionBookDto twice = pipeline.sanitize(once);

// Должно быть true
assertThat(twice).

usingRecursiveComparison().

isEqualTo(once);
```

### 5. Порядок санитайзеров

**Правильный порядок:**

1. Удаление пустых узлов
2. Нормализация текста
3. Проверка ссылок
4. Нормализация атрибутов

**Неправильный порядок:**

```java
// ❌ Плохо: проверяем ссылки до нормализации атрибутов
SanitizerPipeline.builder()
    .

add(new OrphanedLinkCleaner())  // Может не найти якоря из-за невалидных id
        .

add(new AttributeNormalizer())
        .

build();

// ✅ Хорошо: сначала нормализуем атрибуты
SanitizerPipeline.

builder()
    .

add(new AttributeNormalizer())
        .

add(new OrphanedLinkCleaner())  // Теперь якоря валидны
        .

build();
```

---

## Создание собственных санитайзеров

### Простой санитайзер

```java
/**
 * Удаляет все параграфы, содержащие слово "СПОЙЛЕР".
 */
public class SpoilerCleaner implements Sanitizer {

    @Override
    public FictionBookDto sanitize(FictionBookDto book) {
        return FictionBookDtoTransformer.transform(book)
                .onParagraph(this::removeSpoilers)
                .apply();
    }

    private Paragraph removeSpoilers(Paragraph p) {
        String text = extractText(p);
        if (text.contains("СПОЙЛЕР")) {
            return null; // Удаляем параграф
        }
        return p;
    }

    private String extractText(Paragraph p) {
        return p.elements().stream()
                .filter(e -> e instanceof Text)
                .map(e -> ((Text) e).value())
                .collect(Collectors.joining());
    }
}
```

### Санитайзер с параметрами

```java
/**
 * Удаляет параграфы короче указанной длины.
 */
public class ShortParagraphCleaner implements Sanitizer {

    private final int minLength;

    public ShortParagraphCleaner(int minLength) {
        this.minLength = minLength;
    }

    @Override
    public FictionBookDto sanitize(FictionBookDto book) {
        return FictionBookDtoTransformer.transform(book)
                .onParagraph(this::cleanShortParagraphs)
                .apply();
    }

    private Paragraph cleanShortParagraphs(Paragraph p) {
        int length = p.elements().stream()
                .filter(e -> e instanceof Text)
                .mapToInt(e -> ((Text) e).value().length())
                .sum();

        if (length < minLength) {
            return null; // Удаляем короткий параграф
        }
        return p;
    }
}
```

### Использование кастомного санитайзера

```java
SanitizerPipeline pipeline = SanitizerPipeline.builder()
        .add(new EmptyParagraphCleaner())
        .add(new SpoilerCleaner())
        .add(new ShortParagraphCleaner(50))
        .add(new TextNodeMerger())
        .build();

FictionBookDto clean = pipeline.sanitize(book);
```

---

## Производительность

### Оптимизации

1. **Immutable DTO** — безопасны для многопоточки, не требуют синхронизации
2. **Ленивые трансформации** — `FictionBookDtoTransformer` не создаёт копии без необходимости
3. **Короткое замыкание** — если санитайзер вернул оригинал, копия не создаётся

### Benchmark

Типичная книга (500 KB, 50 глав, 1000 параграфов):

| Операция                      | Время  | Память |
|-------------------------------|--------|--------|
| Чтение FB2                    | ~50 ms | ~10 MB |
| Санитизация (полный пайплайн) | ~20 ms | ~5 MB  |
| Запись FB2                    | ~30 ms | ~2 MB  |

**Итого:** обработка книги занимает ~100 ms.

### Советы по оптимизации

```java
// ✅ Хорошо: применяем санитайзеры один раз перед записью
FictionBookDto clean = pipeline.sanitize(book);
FictionBookIO.

write(clean, Path.of("output.fb2"));

// ❌ Плохо: применяем санитайзеры многократно
        for(
Path output :outputs){
FictionBookDto clean = pipeline.sanitize(book); // Избыточно!
    FictionBookIO.

write(clean, output);
}
```

---

## Отладка

### Логирование

Включите DEBUG-логирование для пакета `org.tehlab.whitek0t.fictionbook.internal.sanitizer`:

```properties
# logback.xml
<logger name="org.tehlab.whitek0t.fictionbook.internal.sanitizer" level="DEBUG"/>
```

Пример вывода:

```
DEBUG OrphanedImageCleaner: Broken image reference: #cover (not found in resources)
DEBUG OrphanedLinkCleaner: Broken internal link: #note99 (target not found)
DEBUG AttributeNormalizer: Normalized id '123 invalid!' to '_23_invalid_'
```

### Визуализация изменений

```java
public class DiffReporter {
    public static void report(FictionBookDto before, FictionBookDto after) {
        System.out.println("=== Diff Report ===");
        System.out.println("Sections before: " + countSections(before));
        System.out.println("Sections after:  " + countSections(after));
        System.out.println("Paragraphs before: " + countParagraphs(before));
        System.out.println("Paragraphs after:  " + countParagraphs(after));
    }
}
```

---

## FAQ

### Q: Можно ли отключить санитайзеры?

**A:** Да, передайте `null` в `Fb2Writer`:

```java
writer.setSanitizerPipeline(null);
```

### Q: Почему санитайзеры не удаляют битые ссылки полностью?

**A:** Чтобы не терять текст. Вместо удаления ссылка помечается как битая, и пользователь видит проблему.

### Q: Можно ли добавить свой санитайзер в стандартный пайплайн?

**A:** Да:

```java
SanitizerPipeline custom = SanitizerPipeline.builder()
        .add(new EmptyParagraphCleaner())
        .add(new TextNodeMerger())
        .add(new MyCustomSanitizer())  // Ваш санитайзер
        .add(new OrphanedImageCleaner())
        .add(new OrphanedLinkCleaner())
        .add(new AttributeNormalizer())
        .build();
```

### Q: Санитайзеры потокобезопасны?

**A:** Да, они immutable и не имеют состояния. Можно использовать один экземпляр в нескольких потоках.

### Q: Что делать, если санитайзер выбрасывает исключение?

**A:** `SanitizerPipeline` ловит исключения и логирует их, продолжая работу с остальными санитайзерами. Исходная книга
остаётся без изменений.

---

## Заключение

Санитайзеры — мощный инструмент для гарантии качества FB2/FB3 файлов. Они:

✅ Автоматически применяются при записи  
✅ Приводят книги к строгому соответствию стандарту  
✅ Безопасны (immutable DTO)  
✅ Расширяемы (можно добавлять свои)  
✅ Быстры (~20 ms на типичную книгу)

Используйте стандартный пайплайн `SanitizerPipeline.standard()` для большинства задач, и создавайте кастомные
санитайзеры для специфичных требований вашего проекта.