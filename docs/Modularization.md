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

## Фаза 1. `:core:domain` (1–2 дня, риск низкий)

1. `core/domain/build.gradle.kts`: `com.android.library` + Kotlin, `namespace = "com.infinitecanvas.board.domain"`, зависимости: `kotlinx-coroutines-core`.
2. Перенести `../core/domain`, `../core/domain`, `../core/domain` **без изменения пакетов** — тогда импорты в 60+ файлах не меняются, diff — только перемещения.
3. Добавить сюда же **порты** (интерфейсы), чтобы разорвать `presentation → data`:
   ```kotlin
   // core/domain/.../port/ImageStore.kt
   interface ImageStore {
       fun fileFor(boardId: Long, fileName: String): File
       fun createTempCameraFile(): File
       suspend fun importFromUri(boardId: Long, uri: Uri): ImportedImage?
       suspend fun delete(boardId: Long, fileName: String)
       suspend fun deleteAll(boardId: Long)
   }
   interface BoardThumbnailer {
       suspend fun generateAndSave(boardId: Long, strokes: List<Stroke>, images: List<BoardImage>): String?
   }
   ```
   `ImportedImage` — доменный data-класс (fileName, widthPx, heightPx).
4. Перенести чистые тесты (`FolderSortingTest`) в `core/domain/src/test`; добавить отсутствующие unit-тесты `ViewportState`, `Quadtree`, `LassoSelector` — они чистые, моки не нужны. Это дешёвая страховка перед перемещениями.
5. В `:app` — `implementation(project(":core:domain"))`.

**Критерий:** `core:domain` не содержит `android.*`/`androidx.compose.*`/`androidx.room.*`; тесты домена идут на JVM без Robolectric; `:app` собирается без правок логики.
