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

> **Статус на 2026-11-26: фазы 0–5 выполнены.**
> Фактическая структура: `:app` (MainActivity + AppContainer + AppNavHost), `:core:domain`,
> `:core:data`, `:core:canvas`, `:core:designsystem`, `:core:testing`, `:feature:home`,
> `:feature:board`, `build-logic/` (convention-плагины). Границы проверяются
> `scripts/check-module-boundaries.ps1` (deps + импорты + чужой `R` + уникальность namespace),
> зависимости — `buildHealth` (DAGP 3.20.4), CI обновлён. Тесты перенесены в свои модули,
> фикстуры — в `:core:testing`, `:app` сокращён до composition root.
> Остаточные долги: DAGP-советы `api`/транзитивные зависимости не фатальны; `:app` всё ещё
> держит `:core:data` (нужен только `AppContainer`); размер APK/время сборки — замерить.

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

---

## Фаза 0. Подготовка (0,5–1 день)

Без неё двигать код опасно: unit-тесты сейчас **не компилируются**.

1. **Зафиксировать рабочее дерево.** Сейчас незакоммичены `Brush.kt`, `ThumbnailGenerator.kt`, `StrokeGeometryCache.kt`: рефакторинг `calculateWidthForBrush → Brush.calculateWidth` уже сделал `CalculateWidthForBrushTest.kt` невалидным. Варианты:
   - обновить тест на `Brush.calculateWidth` (предпочтительно) и закоммитить;
   - либо вернуть тонкий wrapper `fun calculateWidthForBrush(...) = brush.calculateWidth(...)` на один коммит.
   Критерий: `.\gradlew.bat :app:testDebugUnitTest` зелёный.
2. **Проверить, что в тестах нет «мёртвых» отчётов.** В `app/build/test-results` лежат `BoardFileStorageTest` (7) и `BoardViewModelTest` (2), которых нет в дереве. Решить судьбу: восстановить (рекомендую, это самые рискованные зоны) или осознанно удалить.
3. **DI-шов.** Вынести создание `AppDatabase`, `BoardFileStorage`, `ImageAssetStorage`, `ThumbnailGenerator`, `BoardRepositoryImpl`, Coil `ImageLoader` из `MainActivity` в `di/AppContainer.kt` (обычный класс с ленивыми полями). Никакого Hilt — достаточно ручного контейнера. Это ключевой enabler: модули будут отдавать фабрики, а `app` — связывать.
4. **Room schema export.** `@Database(exportSchema = false)` → `true`, добавить ksp-аргумент `room.schemaLocation` и закоммитить `core/data/schemas/.../1.json`. Без этого миграции при выносе модуля станут необратимыми.
5. **Baseline.** Записать: clean build, incremental build, время `testDebugUnitTest`, размер APK. Подключить `kover`/`jacoco` (сейчас нет) — чтобы доказать, что при переносе кода покрытие не упало.
6. **CI.** GitHub Actions (или локальный скрипт): `gradlew :app:testDebugUnitTest :app:lintDebug`. Instrumented — ночным прогоном на эмуляторе.

**Выход:** зелёные тесты, контейнер зависимостей, схема Room в git, метрики «до».

---

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

---

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

---

## Фаза 3. Порты и DI: убрать `presentation → data` (2 дня, риск средний)

Это архитектурное ядро плана; делать **до** выноса фич, пока всё в одном модуле и легко проверять.

1. `BoardViewModel`, `BoardViewModelFactory`, `BoardScreen`, `CanvasLayer`, `BoardImageController` принимают `ImageStore`/`BoardThumbnailer` вместо `ImageAssetStorage`/`ThumbnailGenerator`. `BoardImageCache` — `ImageStore` (или только `fileFor`-порт `ImageFiles`).
2. `AppNavHost` больше не принимает storage: получает `AppContainer` (или готовые фабрики VM).
3. `BoardViewModel` заодно становится тестируемым: добавить `CoroutineDispatcher`/`Thumbnailer` в конструктор, написать `BoardViewModelTest` на дебаунс 1.5 с, двойной `flush`, `openBoard`-guard, `NonCancellable`.
4. Убрать `@VisibleForTesting`-костыли, где они были нужны из-за конкретных классов.

**Критерий:** `grep -r "data.local.storage" app/src/main/java/com/infinitecanvas/board/presentation` пуст; тест `BoardViewModel` зелёный; поведение не изменилось (ручной смоук: рисование → сохранение → перезапуск → превью).

---

## Фаза 4. `:core:canvas` + `:feature:board` + `:feature:home` (3–5 дней, риск выше)

Порядок именно такой: canvas не зависит от фич, фичи — друг от друга.

1. **`:core:canvas`**: перенести `presentation/canvas/**` целиком. Публичный API — только `InfiniteCanvasView` + `CanvasGestureHandler` (если нужен снаружи), остальное `internal`; `BoardImageCache` → `ImageFiles`. Зависимости: `:core:domain`, Compose UI/foundation.
2. **`:feature:board`**: `presentation/screen/board/**` → `feature/board`; вход — `@Composable fun BoardRoute(boardId, onBack, deps)`; VM-фабрика внутри модуля принимает порты. HUD/палитра — `internal`.
3. **`:feature:home`**: `presentation/screen/home/**` → `feature/home`; вход — `HomeRoute(onOpenBoard)`. `HomeViewModel` зависит только от `BoardRepository`.
4. **`:core:designsystem`**: `presentation/theme/**` + общие строки (`action_*`, `error_*`) и типографика. Строки разделить по владельцу: board-специфичные → `feature:board/res`, home-специфичные → `feature:home/res`, общие → designsystem. **Важно:** с `nonTransitiveRClass` (по умолчанию в AGP 9) `R` больше не общий — обновить импорты `com.infinitecanvas.board.R` → `...designsystem.R` / `...feature.board.R`; это самый шумный, но механический diff, делать отдельным коммитом.
5. **`:app`**: `MainActivity` + `AppContainer` + `AppNavHost` (маршруты `home`, `../feature/board`) + `Application`/манифест/`app_name`. Цель — < 500 строк.
6. **`build-logic/`**: convention plugins `infcanvas.android.library`, `infcanvas.compose`, `infcanvas.feature`, `infcanvas.jvm`. В них: compileSdk/minSdk, Compose BOM, compiler stability config (`app/stability_config.conf` переезжает в convention), `testOptions` (Robolectric resources, animationsDisabled), тестовые зависимости.
7. Тестовые фикстуры: вынести `app/src/test/.../testing` и `androidTest/.../testing` в один модуль `:core:testing` (`testFixtures` или отдельный `com.android.library` с `testImplementation`), чтобы убрать дубли `BrushFixtures`/`StrokeRealFixtures`.

**Критерий:** `feature:board` не имеет в зависимостях `:core:data`; изменение `feature:home` не перекомпилирует `feature:board`; instrumented-тесты `CanvasGestureIntegrationTest`/`InfiniteCanvasViewTest` переехали в `core:canvas` и зелёные; приложение запускается, навигация и сохранение работают.

---

## Фаза 5. Защита границ и гигиена (1–2 дня)

1. **Dependency-analysis plugin** (`com.autonomousapps.dependency-analysis`) — ловит неиспользуемые и «нелегальные» зависимости, `api` vs `implementation`.
2. **Konsist/ArchUnit-тесты** (или custom Gradle `check`):
   - `domain` не использует `android.*`, `androidx.compose.*`, `androidx.room.*`;
   - `data` не использует `com.infinitecanvas.board.presentation|feature`;
   - `feature:*` не использует `core.data`;
   - в `feature:*` нет ссылок на `R` чужих модулей.
3. Удалить мёртвое и лишнее: `InfCanvasTheme` (или начать использовать), неиспользуемые Truth/Turbine/arch-core/mockk-android/uiautomator (или начать использовать Turbine), дубль-алиас `androidx-ui-test-junit4`, стейл-артефакты `build/test-results` и `reports/jacoco`.
4. Build hygiene: `org.gradle.parallel=true`, `org.gradle.caching=true` (config cache уже есть), CI-матрица unit + lint, ночные connected-тесты.
5. Definition of Done для новых фич: «новая фича — новый модуль или явное обоснование, почему нет».

---

## Порядок и оценка

| Фаза | Содержание | Оценка | Риск |
|---|---|---|---|
| 0 | тесты, DI-контейнер, schema, baseline, CI | 0,5–1 д | низкий |
| 1 | `core:domain` + порты + тесты геометрии | 1–2 д | низкий |
| 2 | `core:data` + тесты формата/репозитория | 2–3 д | средний |
| 3 | порты в presentation, `BoardViewModelTest` | 2 д | средний |
| 4 | `core:canvas`, `feature:board`, `feature:home`, `designsystem`, convention plugins | 3–5 д | выше среднего |
| 5 | dependency-analysis, Konsist, гигиена | 1–2 д | низкий |
| **Итого** | | **~10–15 рабочих дней** одного разработчика | |

Можно остановиться на фазе 2–3: это даёт 80 % пользы (изоляция домена и данных, тестируемость, запрет протечек) при 30 % работы. Вынос фич оправдан, когда появляется второй разработчик, несколько экранов или build time становится проблемой.

---

## Риски и как их снимать

| Риск | Митигация |
|---|---|
| Перенос кода ломает `R`/ресурсы | Отдельный коммит «move + R-fix», `nonTransitiveRClass`, сначала strings в designsystem |
| Потеря покрытия при переносе | Kover-отчёт до/после каждой фазы; тесты переезжают вместе с кодом |
| Room-схема потеряна | `exportSchema = true` + schemas в git **до** выноса data (фаза 0) |
| Циклы зависимостей | Граф из этого документа; `feature:*` не зависят друг от друга; порты — только в domain |
| Рост времени clean build | Не дробить мельче 6–7 модулей; parallel/caching; convention plugins вместо копипасты |
| Смешение рефакторинга и переезда | Один PR = один тип изменений; поведение не меняется, кроме явно согласованных фиксов |
| `presentation` продолжит тянуть data «по привычке» | Фаза 5: CI-правило, падающее на импорте `core.data` в фичах |

## Что НЕ делать

- Не создавать модуль на каждый класс/пакет — 6–7 модулей достаточно.
- Не менять пакеты при переносе (иначе diff на 60 файлов и конфликты).
- Не внедрять Hilt ради модулей: ручной `AppContainer` + фабрики покрывают потребности этого приложения.
- Не выносить `ThumbnailGenerator` в presentation: превью — это data/рендер-утилита.
- Не считать модуляризацию лечением логических проблем: отдельными задачами остаются «файл + Room без транзакции», `ON_STOP`-автосейв, `onCleared`, абсолютный `thumbnailPath`, дебаунс 1.5 с. Их лучше делать сразу после фазы 3, пока контракты свежие.
