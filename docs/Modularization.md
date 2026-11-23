# InfCanvas: план выхода из монолитного `:app`

**Цель:** разбить единственный Gradle-модуль на модули с проверяемыми границами, не меняя поведение приложения и не теряя покрытие тестами.

**Принцип:** сначала подготовка (тесты, DI-шов, схема Room), потом извлечение модулей по одному, от самого «чистого» к самому связанному. Каждая фаза — отдельный зелёный PR, приложение собирается и работает после каждой.

**Текущие факты (замеры):**

| Часть | Файлов | Строк | Android/Compose/Room |
|---|---:|---:|---|
| `domain` | 11 | 622 | нет (0 импортов android/androidx) |
| `data` | 8 | 1 016 | Room, Bitmap, Exif, Context |
| `presentation/canvas` | 22 | 2 440 | Compose, Bitmap |
| `presentation/screen/board` | 13 | 1 920 | Compose, Uri, File |
| `presentation/screen/home` | 16 | 1 464 | Compose, Coil |
| `presentation/theme` | 3 | 98 | Compose |

Связи, которые придётся разрезать (все — `presentation → data`, обратных нет):

- `ImageAssetStorage`: `BoardImageCache`, `AppNavHost`, `BoardScreen`, `BoardViewModel`, `BoardViewModelFactory`, `CanvasLayer`, `BoardImageController` (7 файлов);
- `ThumbnailGenerator`: `AppNavHost`, `BoardViewModel`, `BoardViewModelFactory`;
- `BoardRepositoryImpl`/Room в presentation не протекают — это уже хорошо.

---

## Целевая структура

```
InfCanvas/
├── app/                        # только точка входа: MainActivity, AppContainer, AppNavHost, App
├── core/
│   ├── domain/                 # Android-library БЕЗ Compose/Room: модели, spatial, контракты + порты
│   ├── data/                   # Room, файловые хранилища, ThumbnailGenerator, реализация репозитория
│   ├── canvas/                 # InfiniteCanvasView, gestures/, renderer/, кэши
│   └── designsystem/           # тема, Color, Type, общие строки/иконки
├── feature/
│   ├── home/                   # HomeScreen/VM, карточки, диалоги, sidebar
│   └── board/                  # BoardScreen/VM, контроллеры, HUD, палитра
└── build-logic/                # convention plugins (android-library, compose, feature)
```

### Граф зависимостей (стрелка = `implementation`)

```
app ──> feature:home ──> core:designsystem
 │          └─────────> core:domain
 ├──> feature:board ──> core:canvas ──> core:domain
 │          ├─────────> core:designsystem
 │          └─────────> core:domain
 ├──> core:data ──────> core:domain
 └──> core:designsystem
```

Жёсткие инварианты (проверяются в CI, см. фазу 5):

1. `core:domain` не зависит ни от чего, кроме `kotlinx-coroutines-core`.
2. `core:data` не зависит от `presentation`/`feature`/`core:canvas`; `core:canvas` не зависит от `core:data`.
3. `feature:board` и `feature:home` **не** зависят от `core:data` — только от портов из `core:domain`.
4. `app` — единственное место, где создаются реализации (composition root).

> **Про `core:domain`:** рекомендую Android-library (а не `kotlin("jvm")`), потому что порты картинок используют `Uri`/`File`. Запрет на Compose/Room сохраняется и проверяется автоматически. Если хочется 100% JVM-модуль — сделайте порты без `Uri` (`ImageSource`), это +1 день и лишний слой.

## Фаза 2. `:core:data` (2–3 дня, риск средний)

1. Модуль `core/data` (`com.android.library`): Room (+KSP), `exifinterface`, `kotlinx-coroutines`, `:core:domain`.
2. Перенести `../core/data`, `../core/data`, `../core/data`. `ImageAssetStorage : ImageStore`, `ThumbnailGenerator : BoardThumbnailer`.
3. **Разорвать `ThumbnailGenerator` ↔ presentation окончательно:** он должен зависеть только от `Brush.calculateWidth` (domain) и собственного рендера. В HEAD он ещё импортирует `presentation.canvas.calculateWidthForBrush` — фаза 0 это устраняет; проверить `grep`.
4. Room: `exportSchema = true`, схемы в `core/data/schemas`, `room.schemaLocation` в KSP; DAO/entities остаются `internal` (или `public` только для `BoardRepositoryImpl`).
5. Тесты (перенос + новые):
   - `BoardFileStorageTest`: round-trip текущей версии, чтение v1/v2/v3/v4, битый magic, обрезанный файл, `strokeCount`/`pointCount` с мусором (проверка на OOM/`IllegalArgumentException`);
   - `BoardRepositoryImplTest`: фейковый DAO + временная папка, порядок «файл → Room», `thumbnailPath = null` не затирает превью, удаление;
   - `ThumbnailGeneratorTest` (Robolectric): пустая доска, штрихи, отсутствующий файл картинки, размер webp.
6. `:app` подключает `:core:data` и биндит реализации в `AppContainer`.

**Критерий:** `core:data` не импортирует `presentation`/`feature`/`canvas`; data-тесты идут отдельной задачей; схема Room в git.
